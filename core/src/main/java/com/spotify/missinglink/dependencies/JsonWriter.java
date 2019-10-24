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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.Set;

class JsonWriter {
  static JSONObject toJsonObject(ArtifactContainer container) {
    JSONObject object = new JSONObject();
    object.put("coordinate", getCoordinate(container));
    object.put("dependencies", getDeclaredDependencies(container));
    object.put("usages", getUsages(container));
    object.put("classes", getClasses(container));
    object.put("unused", getUnused(container));
    object.put("undeclared", getUndeclared(container));
    object.put("filename", container.getFile().getAbsolutePath());
    return object;
  }

  private static JSONObject getCoordinate(ArtifactContainer container) {
    return container.getCoordinate().toJson();
  }

  private static JSONArray getUnused(ArtifactContainer container) {
    JSONArray array = new JSONArray();
    container.getUndeclared().stream()
            .map(ArtifactContainer::getArtifactName)
            .forEach(array::put);
    return array;
  }

  private static JSONArray getUndeclared(ArtifactContainer container) {
    JSONArray array = new JSONArray();
    container.getUnusedDependencies().stream()
            .map(ArtifactContainer::getArtifactName)
            .distinct()
            .sorted()
            .forEach(array::put);
    return array;
  }

  private static JSONArray getDeclaredDependencies(ArtifactContainer container) {
    JSONArray array = new JSONArray();
    Set<ArtifactContainer> directDependencies = container.getDependencies();
    for (ArtifactContainer dependency : directDependencies) {
      array.put(getDependency(dependency, false));
    }
    for (ArtifactContainer dependency : container.getFlattenedDependencies()) {
      if (!directDependencies.contains(dependency)) {
        array.put(getDependency(dependency, true));
      }
    }
    return array;
  }

  private static JSONObject getUsages(ArtifactContainer container) {
    JSONObject object = new JSONObject();
    Map<String, Set<String>> mappings = container.getMappings();
    mappings.forEach((s, strings) -> object.put(s, new JSONArray(strings)));
    return object;
  }

  private static JSONObject getDependency(ArtifactContainer dependency, boolean transitive) {
    JSONObject object = new JSONObject();
    object.put("coordinate", getCoordinate(dependency));
    object.put("transitive", transitive);
    return object;
  }

  private static JSONArray getClasses(ArtifactContainer container) {
    JSONArray array = new JSONArray();
    container.getDefinedClasses().stream().sorted().forEach(array::put);
    return array;
  }
}
