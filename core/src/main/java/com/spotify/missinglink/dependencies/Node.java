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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class Node<T> {
  private final Map<String, Node<T>> nodes = new HashMap<>();
  private final Map<String, Set<T>> classes = new HashMap<>();
  private Set<Set<T>> dependencies;

  private Node() {
  }

  static <T> Map<String, Set<T>> getDependencyMap(Map<String, Set<T>> dependencies) {
    Node<T> root = new Node<>();
    dependencies.forEach(root::add);
    root.simplify();
    return root.toMap();
  }

  private void add(String className, Set<T> foundIn) {
    Node<T> node = this;
    String[] parts = className.split("\\.");
    String lastPart = parts[parts.length - 1];
    for (int i = 0; i < parts.length - 1; i++) {
      node = node.add(parts[i]);
    }
    node.classes.put(lastPart, foundIn);
  }

  Map<String, Set<T>> toMap() {
    return toMap("", new HashMap<>());
  }

  private Map<String, Set<T>> toMap(String path, Map<String, Set<T>> map) {
    String prepend = path.isEmpty() ? "" : path + ".";
    nodes.forEach((s, node) -> node.toMap(prepend + s, map));
    if (!classes.isEmpty()) {
      classes.forEach((className, foundIn) -> map.put(prepend + className, foundIn));
    }
    return map;
  }


  private Set<Set<T>> getDependencies() {
    if (dependencies == null) {
      dependencies = nodes.values().stream()
              .flatMap(node -> node.getDependencies().stream())
              .collect(Collectors.toSet());
      dependencies.addAll(classes.values());
    }
    return dependencies;
  }

  private void simplify() {
    nodes.values().forEach(Node::simplify);

    Set<Set<T>> dependencies = getDependencies();
    if (dependencies.size() == 1) {
      classes.clear();
      classes.put("**", dependencies.iterator().next());
      nodes.clear();
    } else {
      Set<Set<T>> classDependencies = new HashSet<>(classes.values());
      if (classDependencies.size() == 1) {
        classes.clear();
        classes.put("*", classDependencies.iterator().next());
      }

    }
  }

  private Node<T> add(String part) {
    return nodes.computeIfAbsent(part, p -> new Node<>());
  }
}

