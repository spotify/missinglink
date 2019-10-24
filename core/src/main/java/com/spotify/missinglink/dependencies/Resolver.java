/*
 * Copyright (c) 2019 Spotify AB
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
package com.spotify.missinglink.dependencies;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenArtifactInfo;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenChecksumPolicy;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepositories;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepository;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenUpdatePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Resolver {
  private static final Logger logger = LoggerFactory.getLogger(Resolver.class);
  private static final Set<String> DEFAULT_SCOPES =
          new HashSet<>(Arrays.asList("compile", "provided"));

  // Map of artifact name -> artifact
  private final Map<Coordinate, ArtifactContainer> artifacts = new HashMap<>();

  private final List<ArtifactContainer> roots = new ArrayList<>();

  private final ArtifactCache artifactCache = ArtifactCache.getDefault();

  public static Resolver createFromPomfile(File file) {
    System.out.println("Resolving artifacts from pomfile: " + file);
    List<MavenResolvedArtifact> artifacts = Maven.configureResolver().fromFile("/home/krka/.m2/settings-spotify.xml")
            .loadPomFromFile(file)
            .importDependencies(ScopeType.COMPILE, ScopeType.PROVIDED)
            .resolve().withoutTransitivity().asList(MavenResolvedArtifact.class);

    Resolver resolver = new Resolver();
    for (MavenArtifactInfo artifact : artifacts) {
      resolver.roots.add(resolver.resolve(Coordinate.fromMaven(artifact.getCoordinate())));
    }
    return resolver;
  }

  public static Resolver createFromProject(String filename) {
    BuiltProject builtProject = EmbeddedMaven.forProject(filename)
            .setGoals("clean", "package")
            .build();

    List<Archive> archives = builtProject.getArchives();

    Resolver resolver = new Resolver();
    addModules(resolver, builtProject);
    return resolver;
  }

  public static Resolver createFromCoordinate(String coordinate) {
    return createFromCoordinate(Coordinate.fromString(coordinate));
  }

  public static Resolver createFromCoordinate(Coordinate coordinate) {
    Resolver resolver = new Resolver();
    resolver.roots.add(resolver.resolve(coordinate));
    return resolver;
  }

  private static void addModules(Resolver resolver, BuiltProject module) {
    module.getModules().forEach(submodule -> addModules(resolver, submodule));
    addRoot(resolver, module);
  }

  private static void addRoot(Resolver resolver, BuiltProject builtProject) {
    List<MavenResolvedArtifact> dependencies = builtProject.getModel()
            .getDependencies().stream()
            .filter(dependency -> DEFAULT_SCOPES.contains(dependency.getScope()))
            .map(Coordinate::fromMaven)
            .map(Resolver::resolveMavenArtifact)
            .collect(Collectors.toList());

    Coordinate coordinate = Coordinate.fromModel(builtProject.getModel());

    File file = new File(builtProject.getTargetDirectory(), "classes");

    ArtifactContainer result;
    if (resolver.artifacts.containsKey(coordinate)) {
      ArtifactContainer container = resolver.artifacts.get(coordinate);
      if (container != null) {
        result = container;
      } else {
        throw new CyclicalDependencyException(coordinate);
      }
    } else {
      resolver.artifacts.put(coordinate, null);
      Set<ArtifactContainer> artifactDependencies =
              resolveDependencies(resolver, dependencies, coordinate);
      result = new ArtifactContainerBuilder(coordinate, artifactDependencies)
              .build(file);

      resolver.artifacts.put(coordinate, result);
    }
    resolver.roots.add(result);
  }

  private static Set<ArtifactContainer> resolveDependencies(
          Resolver resolver,
          List<MavenResolvedArtifact> dependencies,
          Coordinate coordinate) {
    try {
      Set<ArtifactContainer> artifactDependencies = new HashSet<>();
      for (MavenArtifactInfo dependency : dependencies) {
        artifactDependencies.add(resolver.resolve(
                Coordinate.fromMaven(dependency.getCoordinate())));
      }
      return artifactDependencies;
    } catch (CyclicalDependencyException e) {
      e.addCoordinate(coordinate);
      throw e;
    }
  }

  public ArtifactContainer resolve(Coordinate coordinate) {
    if (artifacts.containsKey(coordinate)) {
      ArtifactContainer container = artifacts.get(coordinate);
      if (container != null) {
        return container;
      }
      throw new CyclicalDependencyException(coordinate);
    }
    artifacts.put(coordinate, null);

    ArtifactContainer container = artifactCache.resolve(
            this, coordinate,
            () -> {
              MavenResolvedArtifact resolvedArtifact = resolveMavenArtifact(coordinate);
              MavenArtifactInfo[] dependencies = resolvedArtifact.getDependencies();
              File file = resolvedArtifact.asFile();

              try {
                Set<ArtifactContainer> artifactDependencies = Arrays.stream(dependencies)
                        .map(MavenArtifactInfo::getCoordinate)
                        .map(Coordinate::fromMaven)
                        .map(this::resolve)
                        .collect(Collectors.toSet());
                return new ArtifactContainerBuilder(coordinate, artifactDependencies).build(file);
              } catch (CyclicalDependencyException e) {
                e.addCoordinate(coordinate);
                throw e;
              }
            });

    artifacts.put(coordinate, container);
    return container;
  }

  private static MavenResolvedArtifact resolveMavenArtifact(Coordinate coordinate) {
    logger.info("Resolving artifact from coordinate: {}", coordinate);
    return Maven.resolver()
              .resolve(coordinate.toString())
              .withoutTransitivity()
              .asSingleResolvedArtifact();
  }

  public List<ArtifactContainer> getRoots() {
    return roots;
  }

  public Map<Coordinate, ArtifactContainer> getArtifacts() {
    return artifacts;
  }

  public void printDependencyTree() {
    System.out.println("Dependency tree:");
    printDependencies("  ", new HashMap<>(), roots, new AtomicInteger());
  }

  private void printDependencies(String indent,
                                 Map<ArtifactContainer, Integer> visited,
                                 Collection<ArtifactContainer> artifacts,
                                 AtomicInteger currentLine) {
    String nextIndent = indent + "  ";
    for (ArtifactContainer value : artifacts) {
      Set<ArtifactContainer> dependencies = value.getDependencies();
      Integer lineNumber = visited.get(value);
      if (lineNumber != null) {
        String s = indent + value.getCoordinate() + " (see #" + lineNumber + ")";
        System.out.println("     " + s);
      } else {
        String s = indent + value.getCoordinate();
        printWithLine(currentLine, s);
        visited.put(value, currentLine.get());

        if (!dependencies.isEmpty()) {
          //System.out.println("     " + nextIndent + "actual:");
          //value.printDependencies(nextIndent + "  ");
          //System.out.println("     " + nextIndent + "declared:");
          printDependencies(nextIndent + "  ", visited, dependencies, currentLine);
        }
      }
    }
  }

  private void printWithLine(AtomicInteger currentLine, String s) {
    int num = currentLine.incrementAndGet();
    System.out.printf("%3d: %s%n", num, s);
  }

  public void printUnusedWarnings() {
    Set<ArtifactContainer> unused = artifacts.values().stream()
            .filter(container -> !container.getUnusedDependencies().isEmpty())
            .collect(Collectors.toSet());

    if (!unused.isEmpty()) {
      System.out.println("Unused dependencies:");
      unused.forEach(ArtifactContainer::printUnusedDependencies);
    }
  }

  public void printUndeclaredWarnings() {
    Set<ArtifactContainer> undeclared = artifacts.values().stream()
            .filter(container -> !container.getUndeclared().isEmpty())
            .collect(Collectors.toSet());
    if (!undeclared.isEmpty()) {
      System.out.println("Undeclared dependencies:");
      undeclared.forEach(ArtifactContainer::printUndeclaredDependencies);
    }
  }

  public Set<ArtifactContainer> getLatestArtifacts() {
    Map<String, List<Coordinate>> map = new HashMap<>();
    artifacts.keySet().forEach(coordinate ->
            map.computeIfAbsent(coordinate.getArtifactName(), s -> new ArrayList<>()).add(coordinate));
    return map.values().stream()
            .map(coordinates -> coordinates.stream().max(Coordinate.comparator()).get())
            .flatMap(Stream::of)
            .map(this::resolve)
            .collect(Collectors.toSet());

  }
}
