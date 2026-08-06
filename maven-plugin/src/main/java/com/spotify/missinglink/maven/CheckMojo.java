/*-
 * -\-\-
 * missinglink-maven-plugin
 * --
 * Copyright (C) 2016 - 2021 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

/*
 * Copyright (c) 2015 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.missinglink.maven;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;
import com.spotify.missinglink.ArtifactLoader;
import com.spotify.missinglink.Conflict;
import com.spotify.missinglink.Conflict.ConflictCategory;
import com.spotify.missinglink.ConflictChecker;
import com.spotify.missinglink.Java9ModuleLoader;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.Dependency;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;

@Mojo(
    name = "check",
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
    defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class CheckMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  protected MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  protected MavenSession session;

  /** Rapid7 addition: used to attribute a conflict's caller back to a direct dependency. */
  @Component protected DependencyGraphBuilder dependencyGraphBuilder;

  @Parameter(property = "missinglink.skip")
  protected boolean skip = false;

  /**
   * Controls whether the Maven build should be failed if any dependency conflicts are found.
   * Defaults to false.
   */
  @Parameter(defaultValue = "false", property = "failOnConflicts")
  protected boolean failOnConflicts;

  /**
   * Log verbose output. Defaults to false. When false, logs at debug instead, so use `mvn -X` to
   * see output.
   */
  @Parameter(property = "verbose", defaultValue = "false")
  protected boolean verbose;

  /**
   * Rapid7 addition: opt-in whole-classpath / internal-consistency mode. When {@code true}, seed
   * reachability from ALL classes on the checked classpath instead of only the classes reachable
   * from the project's own classes (plus framework-seeded proxy targets). Every call site on the
   * classpath is verified, including library code the project never reaches — which also surfaces
   * latent version skew in dependencies the module does not itself exercise (e.g. lib-only {@code
   * DynamicDurationProperty}/{@code PayloadBackup}-style breaks), so expect more findings that are
   * not actionable from this module.
   *
   * <p>Defaults to {@code false}: the default is framework-aware reachability (see {@link
   * #frameworkAnnotations}), which checks exactly what this module reaches — including the runtime
   * proxy / DI hops that plain static reachability cannot follow — without whole-classpath's
   * lib-only noise. Enable this only when you specifically want to audit the whole classpath's
   * internal consistency rather than this module's own linkage safety.
   */
  @Parameter(property = "missinglink.wholeClasspath", defaultValue = "false")
  protected boolean wholeClasspath;

  /**
   * Rapid7 addition: load and ASM-parse dependency artifacts in parallel. Parsing each jar is
   * independent and CPU-bound, and dominates the wall-clock cost of a run (all classes on the
   * classpath are parsed once, even on a green build). Parallelizing it across cores cuts that cost
   * substantially. Defaults to {@code true}; set to {@code false} to force sequential loading (e.g.
   * to isolate a parsing failure to a specific artifact).
   */
  @Parameter(property = "missinglink.parallelLoad", defaultValue = "true")
  protected boolean parallelLoad;

  /**
   * Rapid7 addition: fully-qualified names of class-level annotations that mark a framework entry
   * point whose real dispatch happens through a runtime-generated proxy or DI wiring (e.g. Retrofit
   * dynamic proxies). When a project class carries one of these annotations, the {@code Class<?>}
   * values named by that class's annotations (such as the configuration class in
   * {@code @RetrofitApi(FooConfig.class)}) are added to the reachability seed set, so version-skew
   * breaks behind the proxy boundary are caught. This is intrinsic to the default reachability path
   * — it patches static reachability's blind spot for reflection/DI-mediated dispatch, which is why
   * framework-aware reachability (not whole-classpath) is the default. Ignored when {@code
   * wholeClasspath} is {@code true}, since that mode already seeds everything.
   *
   * <p>Defaults to Rapid7's Retrofit annotations. Configure via {@code
   * -Dmissinglink.frameworkAnnotations=a.b.C,d.e.F} or the {@code <frameworkAnnotations>} element.
   */
  @Parameter(property = "missinglink.frameworkAnnotations")
  protected List<String> frameworkAnnotations = new ArrayList<>();

  /**
   * Rapid7 addition: default framework annotations applied when {@link #frameworkAnnotations} is
   * left unconfigured. Kept separate from the field initializer because Maven's component
   * configurator overwrites a {@code List} field's initializer with an empty list when the
   * corresponding element is absent from the plugin {@code <configuration>}.
   */
  private static final List<String> DEFAULT_FRAMEWORK_ANNOTATIONS =
      Arrays.asList(
          "com.rapid7.platform.retrofit.annotation.RetrofitApi",
          "com.rapid7.platform.retrofit.annotation.EnableRetrofitApi");

  /**
   * Limit the conflict output to only the specified categories. If not set, uses all categories of
   * conflicts.
   */
  @Parameter(property = "missinglink.includeCategories")
  protected List<String> includeCategories = new ArrayList<>();

  /**
   * Include dependencies with the following scopes in conflict checks. Default is "compile, test".
   */
  @Parameter(property = "missinglink.includeScopes", defaultValue = "compile,test")
  protected List<Scope> includeScopes = new ArrayList<>();

  /**
   * Dependencies of the project to exclude from analysis. Defaults to an empty list. The dependency
   * should be specified as an {@link Exclusion} containing a groupId and artifactId. Classes in
   * these artifacts will not be checked for conflicts.
   */
  @Parameter protected List<Exclusion> excludeDependencies = new ArrayList<>();

  /**
   * Optional list of packages to ignore conflicts in where the source of the conflict is in one of
   * the specified packages.
   *
   * <p>This parameter does not exclude those packages from analysis, but the plugin will not output
   * the conflicts that are found in those packages when the caller side of the conflict is in this
   * package, and they will not count against the {@link #failOnConflicts} setting.
   *
   * <p>For example, if the package "javax.foo" is in ignoreSourcePackages, then any conflict found
   * originating in a javax.foo class is ignored. This is mostly the same behavior as {@link
   * #excludeDependencies} but operates at a package name level instead of a groupId/artifactId
   * level.
   */
  @Parameter protected List<PackageFilter> ignoreSourcePackages = new ArrayList<>();

  /**
   * Optional list of source packages to target in conflict searches. Any conflicts originating in
   * packages outside of this list will be ignored.
   *
   * <p>This parameter CANNOT be used in conjunction with ignoreSourcePackages. Only one can be set.
   */
  @Parameter protected List<PackageFilter> targetSourcePackages = new ArrayList<>();

  /**
   * Optional list of packages to ignore conflicts in where the destination/called-side of the
   * conflict is in one of the specified packages.
   *
   * <p>This parameter does not exclude those packages from analysis, but the plugin will not output
   * the conflicts that are found in those packages when the called side of the conflict is in this
   * package, and they will not count against the {@link #failOnConflicts} setting.
   *
   * <p>For example, if the package "javax.bar" is in ignoreDestinationPackages, then any conflict
   * found having to do with calling a method in a class in javax.bar is ignored.
   *
   * <p>This parameter CANNOT be used in conjunction with targetDestinationPackages. Only one can be
   * set.
   */
  @Parameter protected List<PackageFilter> ignoreDestinationPackages = new ArrayList<>();

  /**
   * Optional list of destination packages to target in conflict searches. Any conflicts originating
   * in packages outside of this list will be ignored.
   *
   * <p>This parameter CANNOT be used in conjunction with ignoreDestinationPackages. Only one can be
   * set.
   */
  @Parameter protected List<PackageFilter> targetDestinationPackages = new ArrayList<>();

  /**
   * Optional: can be set to explicitly define the path to use for the bootclasspath containing the
   * java.* / standard library classes. Note that this value is expected to look like a classpath -
   * various file paths separated by the path separator.
   *
   * <p>When not set, the bootclasspath is determined by examining the "sun.boot.class.path" system
   * on java 8 and below. On java 9 and above will use the modules. property.
   */
  @Parameter(property = "misslink.bootClasspath")
  protected String bootClasspath;

  // TODO 6/1/15 mbrown -- how to hook into the Plexus container for proper DI lookups and the
  // conventional maven plugin way of how to set up things like this
  protected ArtifactLoader artifactLoader = new ArtifactLoader();
  // conflictChecker is initialized in execute() so it can honor the verbose flag (Rapid7: gates
  // the noisy "Cannot find parent ..." warnings).
  protected ConflictChecker conflictChecker = new ConflictChecker();

  public void execute() throws MojoExecutionException, MojoFailureException {

    if (skip) {
      getLog().info("skipping plugin execution since missinglink.skip=true");
      return;
    }

    // when verbose flag is set, log detailed messages to info log. otherwise log to debug. This is
    // so that verbose output from this plugin can be seen easily without having to specify mvn -X.
    final Consumer<String> log = verbose ? msg -> getLog().info(msg) : msg -> getLog().debug(msg);
    // Rapid7: only emit the noisy "Cannot find parent ..." warnings when verbose is set.
    final List<String> effectiveFrameworkAnnotations =
        (frameworkAnnotations == null || frameworkAnnotations.isEmpty())
            ? DEFAULT_FRAMEWORK_ANNOTATIONS
            : frameworkAnnotations;
    conflictChecker = new ConflictChecker(verbose, wholeClasspath, effectiveFrameworkAnnotations);
    logDependencies(log);

    final Set<ConflictCategory> categoriesToInclude;
    try {
      categoriesToInclude =
          includeCategories.stream().map(ConflictCategory::valueOf).collect(Collectors.toSet());
    } catch (IllegalArgumentException e) {
      getLog().error(e);
      throw new MojoExecutionException(
          "Invalid value(s) for 'includeCategories': "
              + includeCategories
              + ". "
              + "Valid choices are: "
              + Joiner.on(", ").join(ConflictCategory.values()));
    }

    if (!ignoreDestinationPackages.isEmpty() && !targetDestinationPackages.isEmpty()) {
      throw new MojoExecutionException(
          "Either ignoreDestinationPackages or targetDestinationPackages can be set, "
              + "but not both.");
    }

    if (!ignoreSourcePackages.isEmpty() && !targetSourcePackages.isEmpty()) {
      throw new MojoExecutionException(
          "Either ignoreSourcePackages or targetSourcePackages can be set, " + "but not both.");
    }

    Collection<Conflict> conflicts = loadArtifactsAndCheckConflicts();
    final int initialCount = conflicts.size();

    conflicts = filterConflicts(conflicts, categoriesToInclude);

    if (conflicts.isEmpty()) {
      getLog().info("No conflicts found");
    } else {
      String warning = conflicts.size() + " conflicts found!";
      if (initialCount != conflicts.size()) {
        warning += " (" + initialCount + " conflicts were found before applying filters)";
      }
      getLog().warn(warning);

      outputConflicts(conflicts);

      if (failOnConflicts) {
        final String message =
            conflicts.size()
                + " class/method conflicts found between source "
                + "code in this project and the runtime dependencies from the Maven"
                + " project. Look above for specific descriptions of each conflict";
        throw new MojoFailureException(message);
      }
    }
  }

  private void logDependencies(Consumer<String> log) {
    // project.getDependencies() only lists the declared dependencies, use .getArtifacts for
    // the transitive dependencies as well
    final ArrayList<org.apache.maven.artifact.Artifact> mavenDependencies =
        Lists.newArrayList(project.getArtifacts());
    Collections.sort(mavenDependencies, Ordering.usingToString());
    log.accept("Project has " + mavenDependencies.size() + " dependencies");
    mavenDependencies.stream().map(art -> "Dependency: " + art.toString()).forEach(log);
  }

  private Collection<Conflict> filterConflicts(
      Collection<Conflict> conflicts, Set<ConflictCategory> categoriesToInclude) {

    if (!categoriesToInclude.isEmpty()) {
      getLog()
          .debug(
              "Only including conflicts from categories: "
                  + Joiner.on(", ").join(categoriesToInclude));

      conflicts =
          filterConflictsBy(
              conflicts,
              c -> categoriesToInclude.contains(c.category()),
              num ->
                  num
                      + " conflicts removed based on includeCategories="
                      + Joiner.on(", ").join(includeCategories)
                      + ". "
                      + "Run plugin again without the 'includeCategories' parameter to see "
                      + "all conflicts that were found.");
    }

    if (!ignoreSourcePackages.isEmpty()) {
      getLog().debug("Ignoring source packages: " + Joiner.on(", ").join(ignoreSourcePackages));

      final Predicate<Conflict> predicate =
          conflict -> !packageIsFiltered(ignoreSourcePackages, conflict.dependency().fromClass());

      conflicts =
          filterConflictsBy(
              conflicts,
              predicate,
              num ->
                  num
                      + " conflicts found in ignored source packages. "
                      + "Run plugin again without the 'ignoreSourcePackages' parameter to see "
                      + "all conflicts that were found.");
    } else if (!targetSourcePackages.isEmpty()) {
      getLog()
          .debug(
              "Checking for conflicts in source packages: "
                  + Joiner.on(", ").join(targetSourcePackages));

      final Predicate<Conflict> predicate =
          conflict -> packageIsFiltered(targetSourcePackages, conflict.dependency().fromClass());

      conflicts =
          filterConflictsBy(
              conflicts,
              predicate,
              num ->
                  num
                      + " conflicts found in targeted source packages. "
                      + "Run plugin again without the 'targetSourcePackages' parameter to see "
                      + "all conflicts that were found.");
    }

    if (!ignoreDestinationPackages.isEmpty()) {
      getLog()
          .debug(
              "Ignoring destination packages: " + Joiner.on(", ").join(ignoreDestinationPackages));

      final Predicate<Conflict> predicate =
          conflict ->
              !packageIsFiltered(ignoreDestinationPackages, conflict.dependency().targetClass());

      conflicts =
          filterConflictsBy(
              conflicts,
              predicate,
              num ->
                  num
                      + " conflicts found in ignored destination packages. "
                      + "Run plugin again without the 'ignoreDestinationPackages' parameter to see "
                      + "all conflicts that were found.");
    } else if (!targetDestinationPackages.isEmpty()) {
      getLog()
          .debug(
              "Checking for conflicts in destination packages: "
                  + Joiner.on(", ").join(targetDestinationPackages));

      final Predicate<Conflict> predicate =
          conflict ->
              packageIsFiltered(targetDestinationPackages, conflict.dependency().targetClass());

      conflicts =
          filterConflictsBy(
              conflicts,
              predicate,
              num ->
                  num
                      + " conflicts found in allowed targeted packages. "
                      + "Run plugin again without the 'targetDestinationPackages' parameter to see "
                      + "all conflicts that were found.");
    }

    return conflicts;
  }

  /**
   * Repeated logic for filtering the collection of Conflicts based on a predicate.
   *
   * @param conflicts conflicts to filter
   * @param predicate predicate to filter by
   * @param logMessage a function that when give the difference in size between the original
   *     collection and filtered collection, produces a message that will be logged as a warning to
   *     the user.
   * @return filtered conflicts
   */
  private Collection<Conflict> filterConflictsBy(
      Collection<Conflict> conflicts,
      Predicate<Conflict> predicate,
      Function<Integer, String> logMessage) {

    final Set<Conflict> filteredConflicts =
        conflicts.stream().filter(predicate).collect(Collectors.toSet());

    if (filteredConflicts.size() != conflicts.size()) {
      final int diff = conflicts.size() - filteredConflicts.size();
      getLog().warn(logMessage.apply(diff));
    }

    return filteredConflicts;
  }

  /**
   * Tests if the Conflict represented by this ClassTypeDescriptor (whether on the source-side or
   * destination-side) is ignored based on the collection of IgnoredPackages. Reusable logic between
   * ignoring source/destination packages.
   */
  private boolean packageIsFiltered(
      Collection<PackageFilter> packageFilters, ClassTypeDescriptor classTypeDescriptor) {
    final String className = classTypeDescriptor.getClassName().replace('/', '.');
    // this might be missing some corner-cases on naming rules:
    // Rapid7 fix: a class in the default (unnamed) package has no '.', so lastIndexOf returns -1;
    // guard against substring(0, -1) throwing. Its package name is the empty string.
    final int lastDot = className.lastIndexOf('.');
    final String conflictPackageName = lastDot < 0 ? "" : className.substring(0, lastDot);

    return packageFilters.stream()
        .anyMatch(
            p -> {
              final String ignoredPackageName = p.getPackage();
              return conflictPackageName.equals(ignoredPackageName)
                  || (p.isFilterSubpackages()
                      && conflictPackageName.startsWith(ignoredPackageName + "."));
            });
  }

  private Collection<Conflict> loadArtifactsAndCheckConflicts() {
    // includes declared and transitive dependencies, anything in the scopes configured to be
    // included
    final List<org.apache.maven.artifact.Artifact> projectDeps =
        this.project.getArtifacts().stream()
            .filter(artifact -> includeScopes.contains(Scope.valueOf(artifact.getScope())))
            .collect(Collectors.toList());

    getLog()
        .debug(
            "project dependencies: "
                + projectDeps.stream().map(this::mavenCoordinates).collect(Collectors.toList()));

    Stopwatch stopwatch = Stopwatch.createStarted();
    // artifacts in runtime scope from the maven project (including transitives)
    final ImmutableList<Artifact> runtimeProjectArtifacts = constructArtifacts(projectDeps);
    stopwatch.stop();
    getLog().debug("constructing runtime artifacts took: " + asMillis(stopwatch) + " ms");

    // also need to load JDK classes from the bootstrap classpath
    final String bootstrapClasspath = bootClassPathToUse();

    stopwatch.reset().start();

    final List<Artifact> bootstrapArtifacts = loadBootstrapArtifacts(bootstrapClasspath);

    stopwatch.stop();
    getLog().debug("constructing bootstrap artifacts took: " + asMillis(stopwatch) + " ms");

    final ImmutableList<Artifact> allArtifacts =
        ImmutableList.<Artifact>builder()
            .addAll(runtimeProjectArtifacts)
            .addAll(bootstrapArtifacts)
            .build();

    final ImmutableList<Artifact> runtimeArtifactsAfterExclusions =
        ImmutableList.copyOf(
            runtimeProjectArtifacts.stream()
                .filter(artifact -> !isExcluded(artifact))
                .collect(Collectors.toSet()));

    final Artifact projectArtifact = toArtifact(project.getBuild().getOutputDirectory());

    if (projectArtifact.classes().isEmpty()) {
      getLog()
          .warn(
              "No classes found in project build directory"
                  + " - did you run 'mvn compile' first?");
    }

    stopwatch.reset().start();

    getLog().debug("Checking for conflicts starting from " + projectArtifact.name().name());
    getLog().debug("Artifacts included in the project: ");
    for (Artifact artifact : runtimeArtifactsAfterExclusions) {
      getLog().debug("    " + artifact.name().name());
    }

    final Collection<Conflict> conflicts =
        conflictChecker.check(projectArtifact, runtimeArtifactsAfterExclusions, allArtifacts);

    stopwatch.stop();
    getLog().debug("conflict checking took: " + asMillis(stopwatch) + " ms");

    getLog().debug(conflicts.size() + " total conflicts found");
    return conflicts;
  }

  private List<Artifact> loadBootstrapArtifacts(final String bootstrapClasspath) {
    if (bootstrapClasspath == null) {
      return Java9ModuleLoader.getJava9ModuleArtifacts((s, ex) -> getLog().warn(s, ex));
    } else {
      return constructArtifacts(
          Arrays.<String>asList(bootstrapClasspath.split(System.getProperty("path.separator"))));
    }
  }

  private String bootClassPathToUse() {
    if (this.bootClasspath != null) {
      getLog().debug("using configured boot classpath: " + this.bootClasspath);
      return this.bootClasspath;
    }

    // Maven executes plugins with a customized ClassLoader to provide isolation between
    // plugins and the Maven installation. If we tried to inspect the 'java.class.path' property,
    // all we would see is a single entry for plexus-classworlds.jar.
    // (more info at https://cwiki.apache.org/confluence/display/MAVEN/Maven+3.x+Class+Loading )
    //
    // To be able to load the Java platform classes (i.e. java.util.*), we have to look at the
    // bootstrap class path - not sure about the standard way to find this.
    // (http://docs.oracle.com/javase/7/docs/technotes/tools/findingclasses.html)
    // TODO 6/4/15 mbrown -- warn users that bootclasspath might be a different version (JAVA_HOME
    // probably) than what they use for javac
    final String bootClasspath = System.getProperty("sun.boot.class.path");
    getLog().debug("derived bootclasspath: " + bootClasspath);
    return bootClasspath;
  }

  private String mavenCoordinates(org.apache.maven.artifact.Artifact dep) {
    return dep.getGroupId()
        + ":"
        + dep.getArtifactId()
        + ":"
        + dep.getVersion()
        + ":"
        + dep.getScope();
  }

  private boolean isExcluded(Artifact artifact) {
    if (artifact.name() instanceof MavenArtifactName) {
      MavenArtifactName name = (MavenArtifactName) artifact.name();
      // excluded if the exclusions lists contains a match
      return excludeDependencies.stream()
          .anyMatch(
              excl ->
                  excl.getGroupId().equals(name.groupId())
                      && excl.getArtifactId().equals(name.artifactId()));
    }
    return false;
  }

  private static long asMillis(Stopwatch stopwatch) {
    return stopwatch.elapsed(TimeUnit.MILLISECONDS);
  }

  /**
   * Rapid7 addition: build the resolved dependency graph so conflicts can be attributed back to the
   * direct dependency that drags in the offending caller artifact. Returns null (and logs) on
   * failure so attribution degrades gracefully rather than breaking the check.
   */
  private DependencyPathFinder buildPathFinder() {
    try {
      final ProjectBuildingRequest req =
          new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
      req.setProject(project);
      final DependencyNode root = dependencyGraphBuilder.buildDependencyGraph(req, null);
      return new DependencyPathFinder(root);
    } catch (Exception e) {
      getLog().warn("Could not build dependency graph for conflict attribution: " + e.getMessage());
      return null;
    }
  }

  private void outputConflicts(Collection<Conflict> conflicts) {
    final DependencyPathFinder pathFinder = buildPathFinder();
    Map<ConflictCategory, String> descriptions = new EnumMap<>(ConflictCategory.class);
    descriptions.put(ConflictCategory.CLASS_NOT_FOUND, "Class being called not found");
    descriptions.put(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND, "Method being called not found");

    // group conflict by category
    final Map<ConflictCategory, List<Conflict>> byCategory =
        conflicts.stream().collect(Collectors.groupingBy(Conflict::category));

    byCategory.forEach(
        (category, conflictsInCategory) -> {
          final String desc =
              descriptions.getOrDefault(category, category.name().replace('_', ' '));
          getLog().warn("");
          getLog().warn("Category: " + desc);

          // next group by artifact containing the conflict
          final Map<ArtifactName, List<Conflict>> byArtifact =
              conflictsInCategory.stream().collect(Collectors.groupingBy(Conflict::usedBy));

          byArtifact.forEach(
              (artifactName, conflictsInArtifact) -> {
                getLog().warn("  In artifact: " + artifactName.name());
                printAttribution(pathFinder, artifactName, conflictsInArtifact);

                // next group by class containing the conflict
                final Map<ClassTypeDescriptor, List<Conflict>> byClassName =
                    conflictsInArtifact.stream()
                        .collect(Collectors.groupingBy(c -> c.dependency().fromClass()));

                byClassName.forEach(
                    (classDesc, conflictsInClass) -> {
                      getLog().warn("    In class: " + classDesc.toString());

                      conflictsInClass.stream()
                          .forEach(
                              c -> {
                                final Dependency dep = c.dependency();
                                getLog()
                                    .warn(
                                        "      In method:  "
                                            + dep.fromMethod().prettyWithoutReturnType()
                                            + optionalLineNumber(dep.fromLineNumber()));
                                getLog().warn("      " + dep.describe());
                                getLog().warn("      Problem: " + c.reason());
                                if (c.existsIn() != ConflictChecker.UNKNOWN_ARTIFACT_NAME) {
                                  getLog().warn("      Found in: " + c.existsIn().name());
                                }
                                // this could be smarter about separating each blob of warnings by
                                // method, but for
                                // now just output a bunch of dashes always
                                getLog().warn("      --------");
                              });
                    });
              });
        });
  }

  /**
   * Rapid7 addition (provider-side attribution): for each conflict, identify the *called* artifact
   * (the one missing the symbol), the version it resolved to on the classpath, which DIRECT
   * dependency forced that version, and — best-effort — the version the caller was originally
   * compiled against. This surfaces the version skew that actually caused the break, so the fix
   * (reconcile the called artifact's version) is attributable to a declared dependency without a
   * manual `mvn dependency:tree -Dverbose` cross-reference.
   *
   * <p>The caller artifact name ("In artifact:") is a "groupId:artifactId:version" string.
   */
  private void printAttribution(
      DependencyPathFinder pathFinder, ArtifactName callerName, List<Conflict> conflicts) {
    if (pathFinder == null) {
      return;
    }
    // The called artifact is the same for all conflicts grouped here only when existsIn matches;
    // collect the distinct providers so multi-provider cases are shown honestly.
    final Set<String> providers = new java.util.LinkedHashSet<>();
    for (Conflict c : conflicts) {
      if (c.existsIn() != ConflictChecker.UNKNOWN_ARTIFACT_NAME) {
        providers.add(c.existsIn().name());
      }
    }
    if (providers.isEmpty()) {
      return;
    }

    final String[] callerParts = callerName.name().split(":");

    for (String provider : providers) {
      final String[] p = provider.split(":");
      if (p.length < 3) {
        continue;
      }
      final String provGroup = p[0];
      final String provArtifact = p[1];
      final String provVersion = p[2];
      final String builtAgainst =
          callerParts.length >= 3
              ? callerCompiledAgainstVersion(
                  callerParts[0], callerParts[1], callerParts[2], provArtifact)
              : null;
      getLog().warn("    Called artifact: " + provGroup + ":" + provArtifact);
      final Set<String> directDeps = pathFinder.directDependenciesFor(provGroup, provArtifact);
      if (directDeps.size() == 1) {
        getLog()
            .warn(
                "      resolved to: "
                    + provVersion
                    + "  (direct dependency "
                    + directDeps.iterator().next()
                    + ")");
      } else if (!directDeps.isEmpty()) {
        getLog()
            .warn(
                "      resolved to: " + provVersion + "  (direct dependencies " + directDeps + ")");
      } else {
        // provider is transitive-only; show the paths so the responsible direct dep is visible
        getLog().warn("      resolved to: " + provVersion + "  (transitive; see paths below)");
      }
      if (builtAgainst != null && !builtAgainst.equals(provVersion)) {
        getLog()
            .warn(
                "      caller "
                    + callerParts[1]
                    + " was compiled against: "
                    + builtAgainst
                    + "  (version skew)");
      }
      for (String path : pathFinder.pathsFor(provGroup, provArtifact)) {
        getLog().warn("      via: " + path);
      }
    }
  }

  /**
   * Best-effort: read the caller artifact's own POM (sibling of its resolved jar in the local repo)
   * and return the version it declares for {@code targetGroupId:targetArtifactId}, i.e. the version
   * the caller was compiled against. Returns null if the POM can't be found/parsed or the
   * dependency is declared via a property or BOM (unresolved here) rather than a literal version.
   */
  private String callerCompiledAgainstVersion(
      String callerGroup, String callerArtifact, String callerVersion, String targetArtifactId) {
    try {
      for (org.apache.maven.artifact.Artifact a : project.getArtifacts()) {
        if (callerGroup.equals(a.getGroupId())
            && callerArtifact.equals(a.getArtifactId())
            && a.getFile() != null) {
          final File jar = a.getFile();
          final File pom =
              new File(jar.getParentFile(), callerArtifact + "-" + callerVersion + ".pom");
          if (!pom.isFile()) {
            return null;
          }
          final Model model;
          try (FileInputStream in = new FileInputStream(pom)) {
            model = new MavenXpp3Reader().read(in);
          }
          if (model.getDependencies() == null) {
            return null;
          }
          return model.getDependencies().stream()
              .filter(d -> d.getVersion() != null && !d.getVersion().contains("${"))
              .filter(d -> targetArtifactId.equals(d.getArtifactId()))
              .map(org.apache.maven.model.Dependency::getVersion)
              .findFirst()
              .orElse(null);
        }
      }
    } catch (Exception e) {
      getLog().debug("could not determine caller's compiled-against version: " + e.getMessage());
    }
    return null;
  }

  private String optionalLineNumber(int lineNumber) {
    return lineNumber != 0 ? ":" + lineNumber : "";
  }

  @SuppressWarnings("UnstableApiUsage")
  private Artifact toArtifact(String outputDirectory) {
    return new ArtifactBuilder()
        .name(new ArtifactName("project"))
        .classes(
            FluentIterable.from(Files.fileTraverser().breadthFirst(new File(outputDirectory)))
                .filter(f -> f.getName().endsWith(".class"))
                .transform(this::loadClass)
                .uniqueIndex(DeclaredClass::className))
        .build();
  }

  private DeclaredClass loadClass(File f) {
    try {
      return com.spotify.missinglink.ClassLoader.load(new FileInputStream(f));
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private ImmutableList<Artifact> constructArtifacts(Iterable<String> entries) {
    // Rapid7: load in parallel. Each artifact is parsed independently (ArtifactLoader/ClassLoader
    // are stateless per call; the only shared state is the ConcurrentHashMap in TypeDescriptors),
    // so this is embarrassingly parallel and CPU-bound — the dominant cost of a green build.
    final List<Artifact> list =
        StreamSupport.stream(entries.spliterator(), parallelLoad)
            // don't inspect paths that don't exist.
            // some bootclasspath entries, like sunrsasign.jar, are reported even if they
            // don't exist on disk - ¯\_(ツ)_/¯
            .distinct()
            .filter(this::filterValidClasspathEntries)
            .map(this::filepathToArtifact)
            .collect(Collectors.toList());
    return ImmutableList.copyOf(list);
  }

  private boolean filterValidClasspathEntries(String element) {
    return filterValid(new File(element));
  }

  private boolean filterValid(File file) {
    if (file == null) {
      return false;
    }
    final boolean isJarFile = file.isFile() && file.getName().endsWith(".jar");
    final boolean isClassDirectory = file.isDirectory();
    return isClassDirectory || isJarFile;
  }

  private boolean filterValidClasspathEntries(org.apache.maven.artifact.Artifact artifact) {
    return filterValid(artifact.getFile());
  }

  private ImmutableList<Artifact> constructArtifacts(
      List<org.apache.maven.artifact.Artifact> mavenDeps) {

    // Rapid7: parallel load — see the sibling constructArtifacts(Iterable<String>) for rationale.
    final List<Artifact> list =
        (parallelLoad ? mavenDeps.parallelStream() : mavenDeps.stream())
            .filter(this::filterValidClasspathEntries)
            .map(this::mavenDepToArtifact)
            .collect(Collectors.toList());
    return ImmutableList.copyOf(list);
  }

  private Artifact filepathToArtifact(String path) {
    getLog().debug("loading artifact for path: " + path);
    return doArtifactLoad(() -> artifactLoader.load(new File(path)));
  }

  private Artifact mavenDepToArtifact(org.apache.maven.artifact.Artifact dep) {
    final File path = dep.getFile();
    getLog().debug("loading artifact for path: " + path);

    final MavenArtifactName name =
        new MavenArtifactName(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());

    return doArtifactLoad(() -> artifactLoader.load(name, path));
  }

  private Artifact doArtifactLoad(ArtifactSupplier supplier) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    Artifact artifact;
    try {
      artifact = supplier.load();
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
    stopwatch.stop();
    getLog().debug("artifact loading took " + asMillis(stopwatch) + " ms");
    return artifact;
  }

  // workaround for java.util.function.Supplier not allowing exceptions to be thrown
  @FunctionalInterface
  private interface ArtifactSupplier {

    Artifact load() throws IOException;
  }
}
