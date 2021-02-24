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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ArtifactContainer {

  private final Coordinate coordinate;

  // Direct declared dependencies
  private final Set<ArtifactContainer> dependencies;

  // All transitive dependencies
  private final Set<ArtifactContainer> flattenedDependencies;

  // Set of declared dependencies that are not used
  private final Set<ArtifactContainer> unusedDependencies;

  // Set of classes that this artifact defines
  private final Set<String> definedClasses;

  // Map of packages/class prefix -> artifacts that define that class
  private final Map<String, Set<String>> mappings;

  // Set of dependencies that are used, but not explicitly declared
  private final Set<ArtifactContainer> undeclared;


  public ArtifactContainer(
          Coordinate coordinate,
          Set<ArtifactContainer> dependencies,
          Set<ArtifactContainer> flattenedDependencies,
          Set<ArtifactContainer> unusedDependencies,
          Set<String> definedClasses,
          Map<String, Set<String>> mappings,
          Set<ArtifactContainer> undeclared) {
    this.coordinate = coordinate;
    this.dependencies = dependencies;
    this.flattenedDependencies = flattenedDependencies;
    this.unusedDependencies = unusedDependencies;
    this.definedClasses = definedClasses;
    this.mappings = mappings;
    this.undeclared = undeclared;
  }

  public boolean definesClass(String className) {
    return definedClasses.contains(className);
  }

  public Set<ArtifactContainer> getFlattenedDependencies() {
    return flattenedDependencies;
  }

  @Override
  public String toString() {
    return getCoordinate().toString();
  }

  public void printDependencies(String indent) {
    mappings.forEach((prefix, artifacts) -> {
      if (artifacts.isEmpty()) {
        System.out.println(indent + prefix + " expected in runtime");
      } else {
        System.out.println(indent + prefix + " found in " + artifacts);
      }
    });

    unusedDependencies.stream().map(ArtifactContainer::getCoordinate).forEach(s ->
            System.out.println(indent + "Unused: " + s));
  }

  public void printUndeclaredDependencies() {
    if (!undeclared.isEmpty()) {
      System.out.println(getCoordinate() + " has undeclared dependencies on " + undeclared);
    }
  }

  public void printUnusedDependencies() {
    if (!unusedDependencies.isEmpty()) {
      Set<String> unused = unusedDependencies.stream()
              .map(ArtifactContainer::getArtifactName)
              .collect(Collectors.toSet());
      System.out.println(getCoordinate() + " has unused dependencies on " + unused);
    }
  }

  public Set<ArtifactContainer> getDependencies() {
    return dependencies;
  }

  public Coordinate getCoordinate() {
    return coordinate;
  }

  public String getArtifactName() {
    return coordinate.getArtifactName();
  }

  public Map<String, Set<String>> getMappings() {
    return mappings;
  }

  public Set<String> getUsages(String className) {
    Set<String> usages = mappings.get(className);
    if (usages != null) {
      return usages;
    }

    usages = mappings.get("**");
    if (usages != null) {
      return usages;
    }

    int lastPeriod = className.lastIndexOf('.');
    if (lastPeriod == -1) {
      // No package name at all, try any root package
      usages = mappings.get("*");
      if (usages != null) {
        return usages;
      }
    }
    String packageName = className.substring(0, lastPeriod - 1);
    return getUsagesForPackage(packageName);
  }

  private Set<String> getUsagesForPackage(String packageName) {
    Set<String> usages = mappings.get(packageName + ".**");
    if (usages != null) {
      return usages;
    }
    usages = mappings.get(packageName + ".*");
    if (usages != null) {
      return usages;
    }
    int lastPeriod = packageName.lastIndexOf('.');
    if (lastPeriod == -1) {
      return Collections.emptySet();
    }
    return getUsagesForPackage(packageName.substring(0, lastPeriod - 1));
  }

  public Set<String> getDefinedClasses() {
    return definedClasses;
  }

  public Set<ArtifactContainer> getUnusedDependencies() {
    return unusedDependencies;
  }

  public Set<ArtifactContainer> getUndeclared() {
    return undeclared;
  }
}
