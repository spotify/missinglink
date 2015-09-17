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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;

import com.spotify.missinglink.ArtifactLoader;
import com.spotify.missinglink.Conflict;
import com.spotify.missinglink.Conflict.ConflictCategory;
import com.spotify.missinglink.ConflictChecker;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.Dependency;

import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

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

@Mojo(name = "check", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class CheckMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  protected MavenProject project;

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
   * Limit the conflict output to only the specified categories. If not set, uses all categories of
   * conflicts.
   */
  @Parameter(property = "missinglink.includeCategories")
  protected List<String> includeCategories = new ArrayList<>();

  /**
   * Consider dependencies with the following scopes to be sources of classes available on the
   * classpath at runtime. Default is "compile".
   */
  @Parameter(property = "missinglink.includeScopes", defaultValue = "compile")
  protected List<Scope> includeScopes = new ArrayList<>();

  /**
   * Dependencies of the project to exclude from analysis. Defaults to an empty list. The
   * dependency should be specified as an {@link Exclusion} containing a groupId and artifactId.
   * Classes in these artifacts will not be checked for conflicts.
   */
  @Parameter
  protected List<Exclusion> excludeDependencies = new ArrayList<>();

  /**
   * Optional list of packages to ignore conflicts in where the source of the conflict is in one of
   * the specified packages.
   * <p>
   * This parameter does not exclude those packages from analysis, but the plugin will not
   * output the conflicts that are found in those packages when the caller side of the conflict is
   * in this package, and they will not count against the {@link #failOnConflicts} setting.</p>
   * <p>
   * For example, if the package "javax.foo" is in ignoreSourcePackages, then any conflict
   * found originating in a javax.foo class is ignored. This is mostly the same behavior as {@link
   * #excludeDependencies} but operates at a package name level instead of a groupId/artifactId
   * level. </p>
   */
  @Parameter
  protected List<IgnoredPackage> ignoreSourcePackages = new ArrayList<>();

  /**
   * Optional list of packages to ignore conflicts in where the destination/called-side of the
   * conflict is in one of the specified packages.
   * <p>
   * This parameter does not exclude those packages from analysis, but the plugin will not output
   * the conflicts that are found in those packages when the called side of the conflict is in this
   * package, and they will not count against the {@link #failOnConflicts} setting.</p>
   * <p>
   * For example, if the package "javax.bar" is in ignoreDestinationPackages, then any conflict
   * found having to do with calling a method in a class in javax.bar is ignored.</p>
   */
  @Parameter
  protected List<IgnoredPackage> ignoreDestinationPackages = new ArrayList<>();

  /**
   * Optional: can be set to explicitly define the path to use for the bootclasspath
   * containing the java.* / standard library classes. Note that this value is expected to look like
   * a classpath - various file paths separated by the path separator.
   * <p>
   * When not set, the bootclasspath is determined by examining the "sun.boot.class.path" system
   * property.</p>
   */
  @Parameter(property = "misslink.bootClasspath")
  protected String bootClasspath;


  // TODO 6/1/15 mbrown -- how to hook into the Plexus container for proper DI lookups and the conventional maven plugin way of how to set up things like this
  protected ArtifactLoader artifactLoader = new ArtifactLoader();
  protected ConflictChecker conflictChecker = new ConflictChecker();

  public void execute() throws MojoExecutionException, MojoFailureException {

    if (skip) {
      getLog().info("skipping plugin execution since missinglink.skip=true");
      return;
    }

    // when verbose flag is set, log detailed messages to info log. otherwise log to debug. This is
    // so that verbose output from this plugin can be seen easily without having to specify mvn -X.
    final Consumer<String> log = verbose ? msg -> getLog().info(msg)
                                         : msg -> getLog().debug(msg);
    logDependencies(log);

    final Set<ConflictCategory> categoriesToInclude;
    try {
      categoriesToInclude = includeCategories.stream()
          .map(ConflictCategory::valueOf)
          .collect(Collectors.toSet());
    } catch (IllegalArgumentException e) {
      getLog().error(e);
      throw new MojoExecutionException(
          "Invalid value(s) for 'includeCategories': " + includeCategories + ". "
          + "Valid choices are: " + Joiner.on(", ").join(ConflictCategory.values()));
    }

    getLog().info("Including scopes: " + includeScopes);

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
        final String message = conflicts.size() + " class/method conflicts found between source "
                               + "code in this project and the runtime dependencies from the Maven"
                               + " project. Look above for specific descriptions of each conflict";
        throw new MojoFailureException(message);
      }
    }
  }

  private void logDependencies(Consumer<String> log) {
    // project.getDependencies() only lists the declared dependencies, use .getArtifacts for
    // the transitive dependencies as well
    final ArrayList<org.apache.maven.artifact.Artifact>
        mavenDependencies = Lists.newArrayList(project.getArtifacts());
    Collections.sort(mavenDependencies, Ordering.usingToString());
    log.accept("Project has " + mavenDependencies.size() + " dependencies");
    mavenDependencies.stream()
        .map(art -> "Dependency: " + art.toString())
        .forEach(log);
  }

  private Collection<Conflict> filterConflicts(Collection<Conflict> conflicts,
                                               Set<ConflictCategory> categoriesToInclude) {

    if (!categoriesToInclude.isEmpty()) {
      getLog().debug("Only including conflicts from categories: "
                     + Joiner.on(", ").join(categoriesToInclude));

      conflicts = filterConflictsBy(conflicts, categoriesToInclude::contains,
          num -> num + " conflicts removed based on includeCategories="
                 + Joiner.on(", ").join(includeCategories) + ". "
                 + "Run plugin again without the 'includeCategories' parameter to see "
                 + "all conflicts that were found.");
    }

    if (!ignoreSourcePackages.isEmpty()) {
      getLog().debug("Ignoring source packages: " + Joiner.on(", ").join(ignoreSourcePackages));

      final Predicate<Conflict> predicate = conflict -> !packageIsIgnored(ignoreSourcePackages,
          conflict.dependency().fromClass());

      conflicts = filterConflictsBy(conflicts, predicate,
          num -> num + " conflicts found in ignored source packages. "
                 + "Run plugin again without the 'ignoreSourcePackages' parameter to see "
                 + "all conflicts that were found.");
    }

    if (!ignoreDestinationPackages.isEmpty()) {
      getLog().debug(
          "Ignoring destination packages: " + Joiner.on(", ").join(ignoreDestinationPackages));

      final Predicate<Conflict> predicate = conflict -> !packageIsIgnored(ignoreDestinationPackages,
          conflict.dependency().targetClass());

      conflicts = filterConflictsBy(conflicts, predicate,
          num -> num + " conflicts found in ignored destination packages. "
                 + "Run plugin again without the 'ignoreDestinationPackages' parameter to see "
                 + "all conflicts that were found."
      );
    }

    return conflicts;
  }

  /**
   * Repeated logic for filtering the collection of Conflicts based on a predicate.
   *
   * @param conflicts  conflicts to filter
   * @param predicate  predicate to filter by
   * @param logMessage a function that when give the difference in size between the original
   *                   collection and filtered collection, produces a message that will be logged
   *                   as a warning to the user.
   * @return filtered conflicts
   */
  private Collection<Conflict> filterConflictsBy(Collection<Conflict> conflicts,
                                                 Predicate<Conflict> predicate,
                                                 Function<Integer, String> logMessage) {

    final Set<Conflict> filteredConflicts = conflicts.stream()
        .filter(predicate)
        .collect(Collectors.toSet());

    if (filteredConflicts.size() != conflicts.size()) {
      final int diff = conflicts.size() - filteredConflicts.size();
      getLog().warn(logMessage.apply(diff));
    }

    return filteredConflicts;
  }

  /**
   * Tests if the Conflict represented by this ClassTypeDescriptor (whether on the source-side or
   * destination-side) is ignored based on the collection of IgnoredPackages. Reusable logic
   * between
   * ignoring source/destination packages.
   */
  private boolean packageIsIgnored(Collection<IgnoredPackage> ignoredPackages,
                                   ClassTypeDescriptor classTypeDescriptor) {

    final String className = classTypeDescriptor.getClassName().replace('/', '.');
    // this might be missing some corner-cases on naming rules:
    final String conflictPackageName = className.substring(0, className.lastIndexOf('.'));

    return ignoredPackages.stream()
        .anyMatch(p -> {
          final String ignoredPackageName = p.getPackage();
          return conflictPackageName.equals(ignoredPackageName) ||
                 (p.isIgnoreSubpackages() && conflictPackageName
                     .startsWith(ignoredPackageName + "."));
        });
  }

  private Collection<Conflict> loadArtifactsAndCheckConflicts() {
    // includes declared and transitive dependencies, anything in the scopes configured to be
    // included
    final List<org.apache.maven.artifact.Artifact> projectDeps =
        this.project.getArtifacts().stream()
            .filter(artifact -> includeScopes.contains(Scope.valueOf(artifact.getScope())))
            .collect(Collectors.toList());

    getLog().debug("project dependencies: " + projectDeps.stream()
            .map(this::mavenCoordinates)
            .collect(Collectors.toList())
    );

    final Consumer<String> log = verbose ? msg -> getLog().info(msg)
                                         : msg -> getLog().debug(msg);

    Stopwatch stopwatch = Stopwatch.createStarted();
    // artifacts in runtime scope from the maven project (including transitives)
    final ImmutableList<Artifact> runtimeProjectArtifacts = constructArtifacts(projectDeps);
    stopwatch.stop();
    getLog().debug("constructing runtime artifacts took: " + asMillis(stopwatch) + " ms");

    // also need to load JDK classes from the bootstrap classpath
    final String bootstrapClasspath = bootClassPathToUse();

    stopwatch.reset().start();

    final List<Artifact> bootstrapArtifacts = constructArtifacts(Arrays.<String>asList(
        bootstrapClasspath.split(System.getProperty("path.separator"))));

    stopwatch.stop();
    getLog().debug("constructing bootstrap artifacts took: " + asMillis(stopwatch) + " ms");

    final ImmutableList<Artifact> allArtifacts = ImmutableList.<Artifact>builder()
        .addAll(runtimeProjectArtifacts)
        .addAll(bootstrapArtifacts)
        .build();

    final ImmutableList<Artifact> runtimeArtifactsAfterExclusions = ImmutableList.copyOf(
        runtimeProjectArtifacts.stream()
            .filter(artifact -> !isExcluded(artifact))
            .collect(Collectors.toSet())
    );

    final Artifact projectArtifact = toArtifact(project.getBuild().getOutputDirectory());

    if (projectArtifact.classes().isEmpty()) {
      getLog().warn("No classes found in project build directory"
                    + " - did you run 'mvn compile' first?");
    }

    stopwatch.reset().start();

    final Collection<Conflict> conflicts = conflictChecker.check(
        projectArtifact, runtimeArtifactsAfterExclusions, allArtifacts);

    stopwatch.stop();
    getLog().debug("conflict checking took: " + asMillis(stopwatch) + " ms");

    getLog().debug(conflicts.size() + " total conflicts found");
    return conflicts;
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
    // TODO 6/4/15 mbrown -- warn users that bootclasspath might be a different version (JAVA_HOME probably) than what they use for javac
    final String bootClasspath = System.getProperty("sun.boot.class.path");
    getLog().debug("derived bootclasspath: " + bootClasspath);
    return bootClasspath;
  }

  private String mavenCoordinates(org.apache.maven.artifact.Artifact dep) {
    return
        dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion() + ":" + dep
            .getScope();
  }

  private boolean isExcluded(Artifact artifact) {
    if (artifact.name() instanceof MavenArtifactName) {
      MavenArtifactName name = (MavenArtifactName) artifact.name();
      // excluded if the exclusions lists contains a match
      return excludeDependencies.stream()
          .anyMatch(excl -> excl.getGroupId().equals(name.groupId())
                            && excl.getArtifactId().equals(name.artifactId()));
    }
    return false;
  }

  private static long asMillis(Stopwatch stopwatch) {
    return stopwatch.elapsed(TimeUnit.MILLISECONDS);
  }

  private void outputConflicts(Collection<Conflict> conflicts) {
    Map<ConflictCategory, String> descriptions = new EnumMap<>(ConflictCategory.class);
    descriptions.put(ConflictCategory.CLASS_NOT_FOUND, "Class being called not found");
    descriptions.put(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND, "Method being called not found");

    // group conflict by category
    final Map<ConflictCategory, List<Conflict>> byCategory = conflicts.stream()
        .collect(Collectors.groupingBy(Conflict::category));

    for (ConflictCategory category : byCategory.keySet()) {
      final String desc = descriptions.getOrDefault(category, category.name().replace('_', ' '));
      getLog().warn("");
      getLog().warn("Category: " + desc);

      // next group by artifact containing the conflict
      final Map<ArtifactName, List<Conflict>> byArtifact = byCategory.get(category).stream()
          .collect(Collectors.groupingBy(Conflict::usedBy));

      for (ArtifactName artifactName : byArtifact.keySet()) {
        getLog().warn("  In artifact: " + artifactName.name());

        // next group by class containing the conflict
        final Map<ClassTypeDescriptor, List<Conflict>> byClassName =
            byArtifact.get(artifactName).stream()
                .collect(Collectors.groupingBy(c -> c.dependency().fromClass()));

        for (ClassTypeDescriptor ctd : byClassName.keySet()) {
          getLog().warn("    In class: " + ctd.toString());

          byClassName.get(ctd).stream()
              .forEach(c -> {
                final Dependency dep = c.dependency();
                getLog().warn("      In method:  " + dep.fromMethod().prettyWithoutReturnType()
                              + optionalLineNumber(dep.fromLineNumber()));
                getLog().warn("      " + dep.describe());
                getLog().warn("      Problem: " + c.reason());
                if (c.existsIn() != ConflictChecker.UNKNOWN_ARTIFACT_NAME) {
                  getLog().warn("      Found in: " + c.existsIn().name());
                }
                // this could be smarter about separating each blob of warnings by method, but for
                // now just output a bunch of dashes always
                getLog().warn("      --------");
              });
        }
      }
    }
  }

  private String optionalLineNumber(int lineNumber) {
    return lineNumber != 0 ? ":" + lineNumber : "";
  }

  private Artifact toArtifact(String outputDirectory) {
    return new ArtifactBuilder()
        .name(new ArtifactName("project"))
        .classes(Files.fileTreeTraverser().breadthFirstTraversal(new File(outputDirectory))
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
    final List<Artifact> list = StreamSupport.stream(entries.spliterator(), false)
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

    final List<Artifact> list = mavenDeps.stream()
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

    final MavenArtifactName name = new MavenArtifactName(
        dep.getGroupId(),
        dep.getArtifactId(),
        dep.getVersion()
    );

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
