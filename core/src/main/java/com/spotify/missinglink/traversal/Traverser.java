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
package com.spotify.missinglink.traversal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.spotify.missinglink.datamodel.AccessedField;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.CalledMethod;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredField;
import com.spotify.missinglink.datamodel.DeclaredMethod;
import com.spotify.missinglink.datamodel.MethodDescriptor;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class Traverser {

  final Map<ClassTypeDescriptor, DeclaredClass> knownClasses = Maps.newHashMap();
  final Set<ClassTypeDescriptor> relevantClasses = Sets.newHashSet();

  private final List<ClassTypeDescriptor> multipleDefinitions = Lists.newArrayList();

  private final Map<Node, Node> visited = Maps.newHashMap();
  private final Queue<Node> queue = new LinkedList<>();
  final List<Node> conflicts = Lists.newArrayList();

  public Traverser(List<Artifact> allArtifacts, List<Artifact> artifactsToCheck) {
    for (final Artifact artifact : allArtifacts) {
      for (final DeclaredClass clazz : artifact.classes().values()) {
        final ClassTypeDescriptor className = clazz.className();
        relevantClasses.add(className);
        if (knownClasses.putIfAbsent(className, clazz) == null) {
          multipleDefinitions.add(className);
        }
      }
    }
    for (Artifact artifact : artifactsToCheck) {
      for (final ClassTypeDescriptor className : artifact.classes().keySet()) {
        relevantClasses.add(className);
      }
    }
  }

  public void visit(Artifact artifact) {
    final ArtifactNode from = new ArtifactNode(artifact);

    for (Map.Entry<ClassTypeDescriptor, DeclaredClass> entry : artifact.classes().entrySet()) {
      final ClassTypeDescriptor className = entry.getKey();
      final ClassNode classNode = new ClassNode(this, from, className);
      visit(classNode);

      final DeclaredClass declaredClass = entry.getValue();
      final ImmutableMap<MethodDescriptor, DeclaredMethod> methods = declaredClass.methods();
      for (final Map.Entry<MethodDescriptor, DeclaredMethod> methodEntry : methods.entrySet()) {
        visit(new DeclaredMethodNode(this, from, className, methodEntry.getValue()));
      }
    }
  }

  public void traverse() {
    while (!queue.isEmpty()) {
      final Node node = queue.poll();
      node.validate();
    }
  }

  public List<Node> getConflicts() {
    return conflicts;
  }

  public List<ClassTypeDescriptor> getMultipleDefinitions() {
    return multipleDefinitions;
  }

  void visit(Node node) {
    if (visited.putIfAbsent(node, node) == null) {
      queue.add(node);
    }
  }

  ClassTypeDescriptor findSuperclassWithMethod(
      ClassTypeDescriptor className, CalledMethod calledMethod) {
    final DeclaredClass declaredClass = knownClasses.get(className);
    if (declaredClass == null) {
      return null;
    }
    final MethodDescriptor methodDescriptor = calledMethod.descriptor();
    final DeclaredMethod declaredMethod = declaredClass.methods().get(methodDescriptor);
    if (declaredMethod != null) {
      if (declaredMethod.isStatic() == calledMethod.isStatic()) {
        return className;
      } else {
        return null;
      }
    } else {
      for (ClassTypeDescriptor parentClass : declaredClass.parents()) {
        final ClassTypeDescriptor superclassWithMethod =
            findSuperclassWithMethod(parentClass, calledMethod);
        if (superclassWithMethod != null) {
          return superclassWithMethod;
        }
      }
      return null;
    }
  }

  boolean hasFieldInAnySuperclass(ClassTypeDescriptor className, AccessedField accessedField) {
    final DeclaredClass declaredClass = knownClasses.get(className);
    if (declaredClass == null) {
      return false;
    }
    final DeclaredField declaredField = declaredClass.fields().get(accessedField.name());
    if (declaredField != null) {
      return declaredField.descriptor().equals(accessedField.descriptor());
    } else {
      for (ClassTypeDescriptor parentClass : declaredClass.parents()) {
        if (hasFieldInAnySuperclass(parentClass, accessedField)) {
          return true;
        }
      }
      return false;
    }
  }

}
