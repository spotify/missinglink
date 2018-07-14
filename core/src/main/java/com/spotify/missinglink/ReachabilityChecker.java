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
package com.spotify.missinglink;

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.spotify.missinglink.datamodel.access.FieldAccess;
import com.spotify.missinglink.datamodel.access.MethodCall;
import com.spotify.missinglink.datamodel.state.DeclaredClass;
import com.spotify.missinglink.datamodel.state.DeclaredMethod;
import com.spotify.missinglink.datamodel.type.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptor;
import com.spotify.missinglink.datamodel.type.TypeDescriptor;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

public class ReachabilityChecker {
  private final Map<ClassTypeDescriptor, DeclaredClass> knownClasses;

  private final Queue<DeclaredClass> classesToCheck;
  private final Queue<DeclaredMethod> methodsToCheck;
  private final Set<DeclaredClass> queuedClassesToCheck;
  private final Set<DeclaredMethod> queuedMethodsToCheck;


  private final Set<ClassTypeDescriptor> reachableClass;
  private final Set<DeclaredMethod> reachableMethod;

  private final ImmutableSet<TypeDescriptor> result;

  public ReachabilityChecker(
      ImmutableCollection<DeclaredClass> initialValues,
      Map<ClassTypeDescriptor, DeclaredClass> knownClasses
  ) {
    this.knownClasses = knownClasses;

    classesToCheck = new LinkedList<>(initialValues);
    methodsToCheck = new LinkedList<>();
    queuedClassesToCheck = new HashSet<>(initialValues);
    queuedMethodsToCheck = new HashSet<>();
    reachableClass = Sets.newHashSet();
    reachableMethod = Sets.newHashSet();

    result = computeResult();
  }

  public ImmutableSet<TypeDescriptor> result() {
    return result;
  }

  private ImmutableSet<TypeDescriptor> computeResult() {
    while (!classesToCheck.isEmpty() || !methodsToCheck.isEmpty()) {
      if (!classesToCheck.isEmpty()) {
        DeclaredClass current = classesToCheck.remove();

        if (!reachableClass.add(current.className())) {
          continue;
        }

        updateReachabilityFromClass(current);
      } else {
        DeclaredMethod current = methodsToCheck.remove();

        if (!reachableMethod.add(current)) {
          continue;
        }

        updateReachabilityFromMethod(current);
      }
    }

    return ImmutableSet.<TypeDescriptor>builder()
        .addAll(reachableClass)
        .addAll(reachableMethod.stream().map(DeclaredMethod::owner).collect(toSet()))
        .build();
  }

  private void addClass(DeclaredClass klass) {
    if (queuedClassesToCheck.add(klass)) {
      classesToCheck.add(klass);
    }
  }

  private void addMethod(DeclaredMethod method) {
    if (queuedMethodsToCheck.add(method)) {
      methodsToCheck.add(method);
    }
  }

  private void updateReachabilityFromClass(DeclaredClass current) {
    current.parents()
        .stream()
        .map(knownClasses::get)
        .filter(Objects::nonNull)
        .forEach(this::addClass);

    current.methods().values().forEach(method ->
        method.methodCalls().forEach(this::handleCalledMethod));
    current.methods().values().forEach(method ->
        method.fieldAccesses().forEach(this::handleAccessedField));
  }

  private void updateReachabilityFromMethod(DeclaredMethod current) {
    current.methodCalls().forEach(this::handleCalledMethod);
    current.fieldAccesses().forEach(this::handleAccessedField);
  }

  private void handleCalledMethod(MethodCall current) {
    getUnreachedMethod(current)
        .forEach(this::addMethod);
  }

  private void handleAccessedField(FieldAccess current) {
    getUnreachedMethod(MethodCall.of(current.owner(), MethodDescriptor.staticInit(), -1))
        .forEach(this::addMethod);
  }

  private Stream<DeclaredMethod> getUnreachedMethod(MethodCall method) {
    return Optional.ofNullable(knownClasses.get(method.owner()))
        .flatMap(klass -> Optional.ofNullable(klass.methods().get(method.descriptor())))
        .filter(declaredMethod -> !reachableMethod.contains(declaredMethod))
        .map(Stream::of)
        .orElseGet(Stream::empty);
  }
}
