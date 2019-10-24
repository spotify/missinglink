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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

class ArtifactContainerBuilder {

  private final Coordinate coordinate;

  // Direct declared dependencies
  private final Set<ArtifactContainer> dependencies;

  // Set of classes that are defined in this artifact
  private final Set<String> definedClasses = new HashSet<>();

  // Set of classes that are referenced from this artifact
  private final Set<String> usedClasses = new HashSet<>();

  private final MyClassVisitor myClassVisitor;

  ArtifactContainerBuilder(
          Coordinate coordinate,
          Set<ArtifactContainer> dependencies) {
    this.coordinate = coordinate;
    this.dependencies = dependencies;
    this.myClassVisitor = new MyClassVisitor(this);
  }

  void addDefinition(String className) {
    definedClasses.add(className.replace('/', '.'));
  }

  void addOwner(String owner) {
    if (owner.startsWith("[")) {
      addDescriptor(owner);
    } else {
      addClass(owner);
    }
  }

  void addDescriptor(String descriptor) {
    addDescriptor(Type.getType(descriptor));
  }

  private void addDescriptor(Type type) {
    switch (type.getSort()) {
      case Type.ARRAY:
        addDescriptor(type.getElementType());
        break;
      case Type.OBJECT:
        addClass(type.getInternalName());
        break;
      case Type.METHOD:
        addDescriptor(type.getReturnType());
        for (Type argumentType : type.getArgumentTypes()) {
          addDescriptor(argumentType);
        }
        break;
      default:
        // Do nothing
    }
  }

  void addClass(String className) {
    if (className.startsWith("[")) {
      throw new IllegalArgumentException("Unexpected class: " + className);
    }
    usedClasses.add(className.replace('/', '.'));
  }

  ArtifactContainer build(File file) {
    loadClasses(file);

    usedClasses.removeAll(definedClasses);

    Set<ArtifactContainer> flattenedDependencies = new HashSet<>(dependencies);
    for (ArtifactContainer dependency : dependencies) {
      flattenedDependencies.addAll(dependency.getFlattenedDependencies());
    }

    // Map of class -> artifacts that define that class
    final Map<String, Set<ArtifactContainer>> dependsOnClasses = new HashMap<>();

    for (String className : usedClasses) {
      dependsOnClasses.put(className, findContainers(className, flattenedDependencies));
    }

    Set<String> allUsed = dependsOnClasses.values().stream()
                    .flatMap(Collection::stream)
                    .map(ArtifactContainer::getArtifactName)
                    .collect(Collectors.toSet());

    // Set of declared dependencies that are not used
    final Set<ArtifactContainer> unusedDependencies = new HashSet<>(dependencies);
    unusedDependencies.removeIf(artifactContainer -> isUsed(artifactContainer, allUsed));

    Set<ArtifactContainer> undeclared = dependsOnClasses.values().stream()
                    .filter(this::isMissing)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());

    Map<String, Set<ArtifactContainer>> dependencyMap = Node.getDependencyMap(dependsOnClasses);
    Map<String, Set<String>> mappings = dependencyMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> mapToName(e.getValue())));
    mappings = new TreeMap<>(mappings);


    return new ArtifactContainer(
            coordinate,
            dependencies,
            flattenedDependencies,
            unusedDependencies,
            definedClasses,
            mappings,
            undeclared,
            file);
  }

  private boolean isUsed(ArtifactContainer container, Set<String> allUsed) {
    if (!container.getDefinedClasses().isEmpty()) {
      return allUsed.contains(container.getArtifactName());
    }

    // Remove from unused if at least one of the dependencies are used
    return container.getDependencies().stream()
            .anyMatch(dependency -> isUsed(dependency, allUsed));

  }

  private Set<String> mapToName(Set<ArtifactContainer> value) {
    return value.stream().map(ArtifactContainer::getArtifactName).collect(Collectors.toSet());
  }

  private Set<ArtifactContainer> findContainers(
          String className,
          Set<ArtifactContainer> flattenedDependencies) {
    HashSet<ArtifactContainer> set = new HashSet<>();
    for (ArtifactContainer dependency : flattenedDependencies) {
      if (dependency.definesClass(className)) {
        set.add(dependency);
      }
    }
    return set;
  }

  private boolean isMissing(Set<ArtifactContainer> containers) {
    return containers.stream().noneMatch(dependencies::contains);
  }

  private void loadClasses(File file) {
    try {
      if (file.isFile() && file.getName().endsWith(".jar")) {
        loadJarFile(file);
      } else if (file.isFile() && file.getName().endsWith(".class")) {
        loadClassFile(file);
      } else if (file.isDirectory()) {
        loadClassDirectory(file);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void loadClassDirectory(File directory) {
    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        loadClasses(file);
      }
    }
  }

  private void loadJarFile(File file) throws IOException {
    try (JarFile jarFile = new JarFile(file)) {
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class")) {
          try (InputStream inputStream = jarFile.getInputStream(entry)) {
            loadClass(inputStream);
          }
        }
      }
    }
  }

  private void loadClassFile(File file) throws IOException {
    try (InputStream inputStream = new FileInputStream(file)) {
      loadClass(inputStream);
    }
  }

  private void loadClass(InputStream inputStream) throws IOException {
    ClassReader classReader = new ClassReader(inputStream);
    classReader.accept(myClassVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
  }
}
