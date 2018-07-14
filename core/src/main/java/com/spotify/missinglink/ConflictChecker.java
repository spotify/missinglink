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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.spotify.missinglink.Conflict.ConflictCategory;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.access.FieldAccess;
import com.spotify.missinglink.datamodel.access.MethodCall;
import com.spotify.missinglink.datamodel.dependency.Dependency;
import com.spotify.missinglink.datamodel.dependency.FieldDependencyBuilder;
import com.spotify.missinglink.datamodel.dependency.MethodDependencyBuilder;
import com.spotify.missinglink.datamodel.state.DeclaredClass;
import com.spotify.missinglink.datamodel.state.DeclaredField;
import com.spotify.missinglink.datamodel.state.DeclaredFieldBuilder;
import com.spotify.missinglink.datamodel.state.DeclaredMethod;
import com.spotify.missinglink.datamodel.type.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptor;
import com.spotify.missinglink.datamodel.type.TypeDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Inputs:
 * <p>
 * 1) The full set of artifact dependencies (D). This can be extracted from the dependency graph.
 * The exact structure of the graph is not interesting, only knowing which dependencies are a part
 * of it. Note that this means that the same library can appear multiple times (with different
 * versions)
 * 2) The classpath of artifacts that is actually used (C). Ordering is important here. If a class
 * appears
 * more than once, the first occurrence will be used.
 * <p>
 * Assumptions:
 * <p>
 * 1) Each artifact could be compiled successfully using their own dependencies.
 * 2) Each artifact was compiled against the same JDK, or at the very least only using parts of the
 * JDK
 * that didn't change compatibility. This is not a fully safe assumption, but to catch the
 * kind of problems that could occur due to this would need a more broad analysis.
 * <p>
 * Strategy:
 * 1) Identify which classes are a part of D but does not exist in C. This is the missing set (M)
 * 2) Identify which classes are a part of D but are replaced in D (or is not the first occurrence)
 * This is the suspicious set (S)
 * 3) Walk through the class hierarchy graph.
 * If something depends on something in M, also add that class to M.
 * If something depends on something in S, also add that class to S
 * 4) Walk through the method call graph, starting from the main entry point (the primary artifact)
 * Whenever a method call is reached, check if the class and method exists.
 * If it doesn't exist, also look in parent classes
 * (implementations could exist both in superclasses and interfaces).
 * <p>
 * Note that we only need to try to verify the method if it's made to a class that is in M or S.
 * If it is in M: fail.
 * If it is in S: check it.
 * <p>
 * If we don't have access to one of the parents, we could simply assume that the call is safe
 * This would however lead to all methods being marked as safe, since everything ultimately
 * inherits from Object (or some other jdk class).
 * <p>
 * The alternative is to mark such calls as failures, which may lead to false positives.
 * This might be ok for the MVP.
 * <p>
 * So we need to have the JDK classes (or some other provided dependencies) as input
 * in order to lookup inheritance.
 * <p>
 * <p>
 * <p>
 * For now, this is not really in place - we simply just look at all the things in the classpath
 */
public class ConflictChecker {

  public static final ArtifactName UNKNOWN_ARTIFACT_NAME = new ArtifactName("<unknown>");

  /**
   * @param projectArtifacts the main artifact of the project we're verifying
   *                         (this is considered the entry point for reachability)
   * @param artifactsToCheck all artifacts that are on the runtime classpath
   * @param allArtifacts     all artifacts, including implicit artifacts (runtime provided
   *                         artifacts)
   * @return a list of conflicts
   */
  public ImmutableList<Conflict> check(List<Artifact> projectArtifacts,
                                       List<Artifact> artifactsToCheck,
                                       List<Artifact> allArtifacts) {

    final CheckerStateBuilder stateBuilder = new CheckerStateBuilder();

    createCanonicalClassMapping(stateBuilder, allArtifacts);
    CheckerState state = stateBuilder.build();

    // brute-force reachability analysis
    Set<TypeDescriptor> reachableClasses =
        reachableFrom(ImmutableList.copyOf(
            projectArtifacts.stream()
                    .flatMap(x -> x.classes().values().stream())
                    .collect(Collectors.toList())),
            state.knownClasses());

    final ImmutableList.Builder<Conflict> builder = ImmutableList.builder();

    // Then go through everything in the classpath to make sure all the method calls / field
    // references are satisfied.
    for (Artifact artifact : artifactsToCheck) {
      for (DeclaredClass clazz : artifact.classes().values()) {
        if (!reachableClasses.contains(clazz.className())) {
          continue;
        }

        for (DeclaredMethod method : clazz.methods().values()) {
          checkForBrokenMethodCalls(state, artifact, method, builder);
          checkForBrokenFieldAccess(state, artifact, method, builder);
        }
      }
    }
    return builder.build();
  }

  /**
   * Create a canonical mapping of which classes are kept. First come first serve in the classpath
   *
   * @param stateBuilder conflict checker state we're populating
   * @param allArtifacts maven artifacts to populate checker state with
   */
  private void createCanonicalClassMapping(CheckerStateBuilder stateBuilder,
                                           List<Artifact> allArtifacts) {
    for (Artifact artifact : allArtifacts) {
      for (DeclaredClass clazz : artifact.classes().values()) {
        if (stateBuilder.knownClasses().putIfAbsent(clazz.className(), clazz) == null) {
          stateBuilder.putSourceMapping(clazz.className(), artifact.name());
        }
      }
    }
  }

  private void checkForBrokenMethodCalls(CheckerState state, Artifact artifact,
                                         DeclaredMethod method,
                                         ImmutableList.Builder<Conflict> builder) {
    for (MethodCall methodCall : method.methodCalls()) {
      final ClassTypeDescriptor owningClass = methodCall.owner();
      final DeclaredClass calledClass = state.knownClasses().get(owningClass);

      if (calledClass == null) {
        builder.add(conflict(ConflictCategory.CLASS_NOT_FOUND,
            "Class not found: " + owningClass,
            dependency(method, methodCall),
            artifact.name(),
            state.sourceMappings().get(owningClass)
        ));
      } else if (missingMethod(methodCall, calledClass, state.knownClasses())) {
        builder.add(conflict(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND,
            "Method not found: " + methodCall.pretty(),
            dependency(method, methodCall),
            artifact.name(),
            state.sourceMappings().get(owningClass)
        ));
      }
    }
  }

  private void checkForBrokenFieldAccess(CheckerState state, Artifact artifact,
                                         DeclaredMethod method,
                                         ImmutableList.Builder<Conflict> builder) {
    for (FieldAccess field : method.fieldAccesses()) {
      final ClassTypeDescriptor owningClass = field.owner();
      final DeclaredClass calledClass = state.knownClasses().get(owningClass);

      DeclaredField declaredField = new DeclaredFieldBuilder()
          .descriptor(field.descriptor())
          .build();

      if (calledClass == null) {
        builder.add(conflict(ConflictCategory.CLASS_NOT_FOUND,
            "Class not found: " + owningClass,
            dependency(method, field),
            artifact.name(),
            state.sourceMappings().get(owningClass)
        ));
      } else if (missingField(declaredField, calledClass, state.knownClasses())) {
        builder.add(conflict(ConflictCategory.FIELD_NOT_FOUND,
            "Field not found: " + field.pretty(),
            dependency(method, field),
            artifact.name(),
            state.sourceMappings().get(owningClass)
        ));
      } else {
        // Everything is ok!

      }
    }
  }

  public static ImmutableSet<TypeDescriptor> reachableFrom(
      ImmutableCollection<DeclaredClass> values,
      Map<ClassTypeDescriptor, DeclaredClass> knownClasses) {
    return new ReachabilityChecker(values, knownClasses).result();
  }

  private Conflict conflict(ConflictCategory category, String reason,
                            Dependency dependency,
                            ArtifactName usedBy,
                            ArtifactName existsIn) {
    if (existsIn == null) {
      existsIn = UNKNOWN_ARTIFACT_NAME;
    }
    return new ConflictBuilder()
        .category(category)
        .dependency(dependency)
        .reason(reason)
        .usedBy(usedBy)
        .existsIn(existsIn)
        .build();
  }

  private Dependency dependency(DeclaredMethod method,
                                MethodCall methodCall) {
    return new MethodDependencyBuilder()
        .fromOwner(method.owner())
        .fromMethod(method.descriptor())
        .fromLineNumber(methodCall.lineNumber())
        .methodCall(methodCall)
        .build();
  }

  private Dependency dependency(DeclaredMethod method,
                                FieldAccess field) {
    return new FieldDependencyBuilder()
        .fromOwner(method.owner())
        .fromMethod(method.descriptor())
        .fromLineNumber(field.lineNumber())
        .fieldAccess(field)
        .build();
  }

  private boolean missingMethod(MethodCall methodCall, DeclaredClass calledClass,
                                Map<ClassTypeDescriptor, DeclaredClass> classMap) {
    final MethodDescriptor descriptor = methodCall.descriptor();
    final DeclaredMethod method = calledClass.methods().get(descriptor);

    if (method != null) {
      return !Objects.equals(methodCall.descriptor().name(), method.descriptor().name())
        || methodCall.descriptor().isStatic() != method.descriptor().isStatic();
    }

    // Might be defined in a super class
    for (ClassTypeDescriptor parentClass : calledClass.parents()) {
      final DeclaredClass declaredClass = classMap.get(parentClass);
      // ignore null parents - this means that the parent cannot be found, and this error gets
      // reported since the class's constructor tries to call its parent's constructor.
      if (declaredClass != null) {
        if (!missingMethod(methodCall, declaredClass, classMap)) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean missingField(DeclaredField field, DeclaredClass calledClass,
                               Map<ClassTypeDescriptor, DeclaredClass> classMap) {

    if (calledClass.fields().contains(field)) {
      // TODO: also validate return type
      return false;
    }

    // Might be defined in a super class
    for (ClassTypeDescriptor parentClass : calledClass.parents()) {
      final DeclaredClass declaredClass = classMap.get(parentClass);
      // TODO 6/2/15 mbrown -- treat properly, by flagging as a different type of Conflict
      if (declaredClass == null) {
        System.out.printf("Warning: Cannot find parent %s of class %s%n",
            parentClass,
            calledClass.className());
      } else if (!missingField(field, declaredClass, classMap)) {
        return false;
      }
    }

    return true;
  }

}
