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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class IncompleteArtifact {
  private final Coordinate coordinate;
  private final Set<Coordinate> dependencies;
  private final Set<String> definedClasses;
  private final Map<String, Set<String>> mappings;
  private final Set<String> unused;
  private final Set<String> undeclared;

  IncompleteArtifact(
          Coordinate coordinate,
          Set<Coordinate> dependencies,
          Set<String> definedClasses,
          Map<String, Set<String>> mappings,
          Set<String> unused,
          Set<String> undeclared) {

    this.coordinate = coordinate;
    this.dependencies = dependencies;
    this.definedClasses = definedClasses;
    this.mappings = mappings;
    this.unused = unused;
    this.undeclared = undeclared;
  }

  Set<Coordinate> getDependencies() {
    return dependencies;
  }

  ArtifactContainer complete(Set<ArtifactContainer> dependencies) {
    HashSet<ArtifactContainer> flattenedDependencies =
            new HashSet<>(dependencies);
    for (ArtifactContainer dependency : dependencies) {
      flattenedDependencies.addAll(dependency.getFlattenedDependencies());
    }

    Set<ArtifactContainer> unusedDependencies = filter(dependencies, unused);
    Set<ArtifactContainer> undeclaredDependencies = filter(dependencies, undeclared);
    return new ArtifactContainer(coordinate, dependencies, flattenedDependencies,
            unusedDependencies, definedClasses, mappings, undeclaredDependencies);
  }

  private static Set<ArtifactContainer> filter(
          Set<ArtifactContainer> dependencies,
          Set<String> names) {
    return dependencies.stream()
            .filter(artifact -> names.contains(artifact.getArtifactName()))
            .collect(Collectors.toSet());
  }

}
