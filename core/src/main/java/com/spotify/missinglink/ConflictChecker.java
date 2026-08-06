/*-
 * -\-\-
 * missinglink-core
 * --
 * Copyright (C) 2016 - 2021 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

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

import static java.util.stream.Collectors.toList;

import com.spotify.missinglink.Conflict.ConflictCategory;
import com.spotify.missinglink.datamodel.AccessedField;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.CalledMethod;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredField;
import com.spotify.missinglink.datamodel.DeclaredFieldBuilder;
import com.spotify.missinglink.datamodel.DeclaredMethod;
import com.spotify.missinglink.datamodel.Dependency;
import com.spotify.missinglink.datamodel.FieldDependencyBuilder;
import com.spotify.missinglink.datamodel.MethodDependencyBuilder;
import com.spotify.missinglink.datamodel.MethodDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Inputs:
 *
 * <p>1) The full set of artifact dependencies (D). This can be extracted from the dependency graph.
 * The exact structure of the graph is not interesting, only knowing which dependencies are a part
 * of it. Note that this means that the same library can appear multiple times (with different
 * versions) 2) The classpath of artifacts that is actually used (C). Ordering is important here. If
 * a class appears more than once, the first occurrence will be used.
 *
 * <p>Assumptions:
 *
 * <p>1) Each artifact could be compiled successfully using their own dependencies. 2) Each artifact
 * was compiled against the same JDK, or at the very least only using parts of the JDK that didn't
 * change compatibility. This is not a fully safe assumption, but to catch the kind of problems that
 * could occur due to this would need a more broad analysis.
 *
 * <p>Strategy: 1) Identify which classes are a part of D but does not exist in C. This is the
 * missing set (M) 2) Identify which classes are a part of D but are replaced in D (or is not the
 * first occurrence) This is the suspicious set (S) 3) Walk through the class hierarchy graph. If
 * something depends on something in M, also add that class to M. If something depends on something
 * in S, also add that class to S 4) Walk through the method call graph, starting from the main
 * entry point (the primary artifact) Whenever a method call is reached, check if the class and
 * method exists. If it doesn't exist, also look in parent classes (implementations could exist both
 * in superclasses and interfaces).
 *
 * <p>Note that we only need to try to verify the method if it's made to a class that is in M or S.
 * If it is in M: fail. If it is in S: check it.
 *
 * <p>If we don't have access to one of the parents, we could simply assume that the call is safe
 * This would however lead to all methods being marked as safe, since everything ultimately inherits
 * from Object (or some other jdk class).
 *
 * <p>The alternative is to mark such calls as failures, which may lead to false positives. This
 * might be ok for the MVP.
 *
 * <p>So we need to have the JDK classes (or some other provided dependencies) as input in order to
 * lookup inheritance.
 *
 * <p>
 *
 * <p>
 *
 * <p>For now, this is not really in place - we simply just look at all the things in the classpath
 */
public class ConflictChecker {

  public static final ArtifactName UNKNOWN_ARTIFACT_NAME = new ArtifactName("<unknown>");

  /**
   * Rapid7 addition: whether to log "Cannot find parent ..." warnings. These fire whenever a called
   * class's superclass is not on the analyzed classpath (e.g. third-party parents the project does
   * not ship) and are almost always noise — they can flood the build log with thousands of lines.
   * Defaults to false (suppressed). Enable for debugging via {@link #ConflictChecker(boolean)}.
   */
  private final boolean logUnresolvedParents;

  /**
   * Rapid7 addition: when true, seed reachability analysis with ALL classes across the artifacts to
   * check (whole-classpath / internal-consistency mode) instead of only the classes reachable from
   * the project's own classes. This catches version-skew breaks in library call sites that the
   * project's own code never reaches. Defaults to false (upstream reachability-seeded behavior).
   */
  private final boolean wholeClasspath;

  /**
   * Rapid7 addition: fully-qualified names (internal form, e.g. {@code
   * com/rapid7/platform/retrofit/annotation/RetrofitApi}) of class-level annotations that mark a
   * framework entry point. When a project class carries one of these, the {@code Class<?>} values
   * referenced by its annotations (e.g. the configuration class named in
   * {@code @RetrofitApi(FooConfig.class)}) are added to the reachability seed set. This closes the
   * blind spot where the real call path runs through a runtime-generated proxy / DI wiring that has
   * no static bytecode edge from the project's own classes. Empty = no framework seeding (upstream
   * behavior).
   */
  private final Set<ClassTypeDescriptor> frameworkAnnotations;

  /** Upstream-compatible constructor; suppresses unresolved-parent warnings. */
  public ConflictChecker() {
    this(false);
  }

  public ConflictChecker(boolean logUnresolvedParents) {
    this(logUnresolvedParents, false);
  }

  public ConflictChecker(boolean logUnresolvedParents, boolean wholeClasspath) {
    this(logUnresolvedParents, wholeClasspath, java.util.Collections.emptySet());
  }

  /**
   * Rapid7 addition.
   *
   * @param frameworkAnnotations annotation FQNs (dot form, e.g. {@code
   *     com.rapid7.platform.retrofit.annotation.RetrofitApi}) whose Class-literal values should be
   *     seeded; converted to internal form here.
   */
  public ConflictChecker(
      boolean logUnresolvedParents,
      boolean wholeClasspath,
      Collection<String> frameworkAnnotations) {
    this.logUnresolvedParents = logUnresolvedParents;
    this.wholeClasspath = wholeClasspath;
    this.frameworkAnnotations =
        frameworkAnnotations.stream()
            .map(name -> TypeDescriptors.fromClassName(name.replace('.', '/')))
            .collect(java.util.stream.Collectors.toSet());
  }

  /**
   * @param projectArtifact the main artifact of the project we're verifying (this is considered the
   *     entry point for reachability)
   * @param artifactsToCheck all artifacts that are on the runtime classpath
   * @param allArtifacts all artifacts, including implicit artifacts (runtime provided artifacts)
   * @return a list of conflicts
   */
  public List<Conflict> check(
      Artifact projectArtifact, List<Artifact> artifactsToCheck, List<Artifact> allArtifacts) {

    final CheckerStateBuilder stateBuilder = new CheckerStateBuilder();

    createCanonicalClassMapping(stateBuilder, allArtifacts);
    CheckerState state = stateBuilder.build();

    // brute-force reachability analysis
    // Rapid7: in whole-classpath mode, seed from every class across artifactsToCheck so that
    // library-internal call sites are analyzed even when the project's own classes never reach
    // them.
    final Collection<DeclaredClass> seed;
    if (wholeClasspath) {
      seed =
          artifactsToCheck.stream()
              .flatMap(a -> a.classes().values().stream())
              .collect(java.util.stream.Collectors.toList());
    } else {
      // Reachability mode: seed from the project's own classes, augmented (when framework
      // annotations are configured) with the Class<?> wiring targets those annotations name.
      final List<DeclaredClass> reachabilitySeed =
          new ArrayList<>(projectArtifact.classes().values());
      reachabilitySeed.addAll(
          frameworkSeeds(projectArtifact.classes().values(), state.knownClasses()));
      seed = reachabilitySeed;
    }
    Set<TypeDescriptor> reachableClasses = reachableFrom(seed, state.knownClasses());

    final List<Conflict> conflicts = new ArrayList<>();

    // Then go through everything in the classpath to make sure all the method calls / field
    // references
    // are satisfied.
    for (Artifact artifact : artifactsToCheck) {
      for (DeclaredClass clazz : artifact.classes().values()) {
        if (!reachableClasses.contains(clazz.className())) {
          continue;
        }

        for (DeclaredMethod method : clazz.methods().values()) {
          conflicts.addAll(checkForBrokenMethodCalls(state, artifact, clazz, method));
          conflicts.addAll(checkForBrokenFieldAccess(state, artifact, clazz, method));
        }
      }
    }
    return conflicts;
  }

  /**
   * Create a canonical mapping of which classes are kept. First come first serve in the classpath
   *
   * @param stateBuilder conflict checker state we're populating
   * @param allArtifacts maven artifacts to populate checker state with
   */
  private void createCanonicalClassMapping(
      CheckerStateBuilder stateBuilder, List<Artifact> allArtifacts) {
    for (Artifact artifact : allArtifacts) {
      for (DeclaredClass clazz : artifact.classes().values()) {
        if (stateBuilder.knownClasses().putIfAbsent(clazz.className(), clazz) == null) {
          stateBuilder.putSourceMapping(clazz.className(), artifact.name());
        }
      }
    }
  }

  private List<Conflict> checkForBrokenMethodCalls(
      CheckerState state, Artifact artifact, DeclaredClass clazz, DeclaredMethod method) {
    List<Conflict> conflicts = new ArrayList<>();

    for (CalledMethod calledMethod : method.methodCalls()) {
      final ClassTypeDescriptor owningClass = calledMethod.owner();
      final DeclaredClass calledClass = state.knownClasses().get(owningClass);

      if (calledClass == null) {
        final boolean catchesNoClassDef =
            calledMethod.caughtExceptions().stream()
                .anyMatch(c -> c.getClassName().equals("java.lang.NoClassDefFoundError"));
        if (!catchesNoClassDef) {
          conflicts.add(
              conflict(
                  ConflictCategory.CLASS_NOT_FOUND,
                  "Class not found: " + owningClass,
                  dependency(clazz, method, calledMethod),
                  artifact.name(),
                  state.sourceMappings().get(owningClass)));
        }
      } else if (missingMethod(calledMethod, calledClass, state.knownClasses())) {
        final boolean catchesNoSuchMethod =
            calledMethod.caughtExceptions().stream()
                .anyMatch(c -> c.getClassName().equals("java.lang.NoSuchMethodError"));
        if (!catchesNoSuchMethod) {
          conflicts.add(
              conflict(
                  ConflictCategory.METHOD_SIGNATURE_NOT_FOUND,
                  "Method not found: " + calledMethod.pretty(),
                  dependency(clazz, method, calledMethod),
                  artifact.name(),
                  state.sourceMappings().get(owningClass)));
        }
      }
    }

    return conflicts;
  }

  private List<Conflict> checkForBrokenFieldAccess(
      CheckerState state, Artifact artifact, DeclaredClass clazz, DeclaredMethod method) {

    List<Conflict> conflicts = new ArrayList<>();

    for (AccessedField field : method.fieldAccesses()) {
      final ClassTypeDescriptor owningClass = field.owner();
      final DeclaredClass calledClass = state.knownClasses().get(owningClass);

      DeclaredField declaredField =
          new DeclaredFieldBuilder().descriptor(field.descriptor()).name(field.name()).build();

      if (calledClass == null) {
        conflicts.add(
            conflict(
                ConflictCategory.CLASS_NOT_FOUND,
                "Class not found: " + owningClass,
                dependency(clazz, method, field),
                artifact.name(),
                state.sourceMappings().get(owningClass)));
      } else if (missingField(declaredField, calledClass, state.knownClasses())) {
        conflicts.add(
            conflict(
                ConflictCategory.FIELD_NOT_FOUND,
                "Field not found: " + field.name(),
                dependency(clazz, method, field),
                artifact.name(),
                state.sourceMappings().get(owningClass)));
      } else {
        // Everything is ok!

      }
    }

    return conflicts;
  }

  /**
   * Rapid7 addition: for each supplied class that carries one of the configured framework
   * annotations, resolve the {@code Class<?>} values named by that class's annotations and return
   * the corresponding {@link DeclaredClass}es (when present on the classpath). Seeding these makes
   * their transitive call sites reachable, so version-skew breaks that only surface behind a
   * runtime proxy / DI boundary (no static edge from the project's own code) are caught in
   * reachability mode. Returns empty when no framework annotations are configured.
   */
  private Collection<DeclaredClass> frameworkSeeds(
      Collection<DeclaredClass> projectClasses,
      Map<ClassTypeDescriptor, DeclaredClass> knownClasses) {
    if (frameworkAnnotations.isEmpty()) {
      return java.util.Collections.emptyList();
    }
    final Set<DeclaredClass> seeds = new HashSet<>();
    for (final DeclaredClass clazz : projectClasses) {
      final boolean isFrameworkEntryPoint =
          clazz.annotations().stream().anyMatch(frameworkAnnotations::contains);
      if (!isFrameworkEntryPoint) {
        continue;
      }
      for (final ClassTypeDescriptor ref : clazz.annotationClassReferences()) {
        final DeclaredClass referenced = knownClasses.get(ref);
        if (referenced != null) {
          seeds.add(referenced);
        }
      }
    }
    return seeds;
  }

  static Set<TypeDescriptor> reachableFrom(
      Collection<DeclaredClass> values, Map<ClassTypeDescriptor, DeclaredClass> knownClasses) {

    Queue<DeclaredClass> toCheck = new LinkedList<>(values);

    Set<TypeDescriptor> reachable = new HashSet<>();

    while (!toCheck.isEmpty()) {
      DeclaredClass current = toCheck.remove();

      if (!reachable.add(current.className())) {
        continue;
      }

      toCheck.addAll(
          current.parents().stream()
              .map(knownClasses::get)
              .filter(declaredClass -> declaredClass != null)
              .collect(toList()));

      toCheck.addAll(
          current.loadedClasses().stream()
              .map(knownClasses::get)
              .filter(declaredClass -> declaredClass != null)
              .collect(toList()));

      toCheck.addAll(
          current.methods().values().stream()
              .flatMap(declaredMethod -> declaredMethod.methodCalls().stream())
              .map(CalledMethod::owner)
              .filter(typeDescriptor -> !reachable.contains(typeDescriptor))
              .map(knownClasses::get)
              .filter(declaredClass -> declaredClass != null)
              .collect(toList()));

      toCheck.addAll(
          current.methods().values().stream()
              .flatMap(declaredMethod -> declaredMethod.fieldAccesses().stream())
              .map(AccessedField::owner)
              .filter(typeDescriptor -> !reachable.contains(typeDescriptor))
              .map(knownClasses::get)
              .filter(declaredClass -> declaredClass != null)
              .collect(toList()));
    }

    return reachable;
  }

  private Conflict conflict(
      ConflictCategory category,
      String reason,
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

  private Dependency dependency(
      DeclaredClass clazz, DeclaredMethod method, CalledMethod calledMethod) {
    return new MethodDependencyBuilder()
        .fromClass(clazz.className())
        .fromMethod(method.descriptor())
        .fromLineNumber(calledMethod.lineNumber())
        .targetMethod(calledMethod.descriptor())
        .targetClass(calledMethod.owner())
        .build();
  }

  private Dependency dependency(DeclaredClass clazz, DeclaredMethod method, AccessedField field) {
    return new FieldDependencyBuilder()
        .fromClass(clazz.className())
        .fromMethod(method.descriptor())
        .fromLineNumber(field.lineNumber())
        .targetClass(field.owner())
        .fieldType(field.descriptor())
        .fieldName(field.name())
        .build();
  }

  private boolean missingMethod(
      CalledMethod calledMethod,
      DeclaredClass calledClass,
      Map<ClassTypeDescriptor, DeclaredClass> classMap) {
    final MethodDescriptor descriptor = calledMethod.descriptor();
    final DeclaredMethod method = calledClass.methods().get(descriptor);

    if (method != null) {
      if (calledMethod.isStatic() != method.isStatic()) {
        return true;
      }

      // TODO: also validate return type
      return false;
    }

    // Might be defined in a super class
    for (ClassTypeDescriptor parentClass : calledClass.parents()) {
      final DeclaredClass declaredClass = classMap.get(parentClass);
      // ignore null parents - this means that the parent cannot be found, and this error gets
      // reported since the class's constructor tries to call its parent's constructor.
      if (declaredClass != null) {
        if (!missingMethod(calledMethod, declaredClass, classMap)) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean missingField(
      DeclaredField field,
      DeclaredClass calledClass,
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
        if (logUnresolvedParents) {
          System.out.printf(
              "Warning: Cannot find parent %s of class %s%n", parentClass, calledClass.className());
        }
      } else if (!missingField(field, declaredClass, classMap)) {
        return false;
      }
    }

    return true;
  }
}
