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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import com.spotify.missinglink.Conflict.ConflictCategory;
import com.spotify.missinglink.datamodel.AccessedField;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.CalledMethod;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredMethod;
import com.spotify.missinglink.datamodel.Dependency;
import com.spotify.missinglink.datamodel.FieldDependencyBuilder;
import com.spotify.missinglink.datamodel.MethodDependencyBuilder;

import static com.spotify.missinglink.Simple.INT;
import static com.spotify.missinglink.Simple.STRING;
import static com.spotify.missinglink.Simple.VOID;
import static com.spotify.missinglink.Simple.array;
import static com.spotify.missinglink.Simple.methodMap;
import static com.spotify.missinglink.Simple.newAccess;
import static com.spotify.missinglink.Simple.newArtifact;
import static com.spotify.missinglink.Simple.newCall;
import static com.spotify.missinglink.Simple.newClass;
import static com.spotify.missinglink.Simple.newMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class FeatureTest {

  private final ConflictChecker conflictChecker = new ConflictChecker();

  @org.junit.Test
  public void testSimpleConflict() throws Exception {

    final DeclaredMethod methodOnlyInD1 = newMethod(INT, "foo").build();
    final DeclaredClass fooClass = newClass("com/d/Foo").methods(methodMap(methodOnlyInD1)).build();

    final Artifact d2 = newArtifact("empty");

    final CalledMethod methodCall = newCall(fooClass, methodOnlyInD1);
    final DeclaredMethod mainMethod = newMethod(VOID, "main")
        .methodCalls(ImmutableSet.of(methodCall)).build();

    final DeclaredClass rootClass = newClass("com/Root")
        .methods(methodMap(mainMethod))
        .build();

    final Artifact root = newArtifact("root", rootClass);

    final ImmutableList<Artifact> classpath = ImmutableList.of(root, d2);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(rootClass.className(), mainMethod, methodCall))
        .reason("Class not found: com.d.Foo")
        .category(ConflictCategory.CLASS_NOT_FOUND)
        .usedBy(root.name())
        .existsIn(ConflictChecker.UNKNOWN_ARTIFACT_NAME)
        .build();

    assertThat(conflictChecker
        .check(root, classpath, classpath))
        .isEqualTo(ImmutableList.of(expectedConflict));
  }

  @org.junit.Test
  public void testSimpleConflict2() throws Exception {

    final DeclaredMethod methodOnlyInD1 = newMethod(INT, "foo").build();
    final DeclaredClass fooClass = newClass("com/d/Foo")
        .methods(methodMap(methodOnlyInD1))
        .build();

    final DeclaredClass d2Class = newClass("com/d/Foo").build();
    final Artifact d2 = newArtifact("D2", d2Class);

    final CalledMethod methodCall = newCall(fooClass, methodOnlyInD1);
    final DeclaredMethod mainMethod = newMethod(VOID, "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .build();

    final DeclaredClass rootClass = newClass("com/Root")
        .methods(methodMap(mainMethod))
        .build();

    final Artifact root = newArtifact("root", rootClass);

    final ImmutableList<Artifact> classpath = ImmutableList.of(root, d2);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(rootClass.className(), mainMethod, methodCall))
        .reason("Method not found: com.d.Foo.foo()")
        .category(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND)
        .usedBy(root.name())
        .existsIn(d2.name())
        .build();

    assertThat(conflictChecker
        .check(root, classpath, classpath))
        .isEqualTo(ImmutableList.of(expectedConflict));
  }

  @org.junit.Test
  public void testMissingField() throws Exception {

    final DeclaredClass d2Class = newClass("com/d/Foo").build();
    final Artifact d2 = newArtifact("D2", d2Class);

    final DeclaredMethod mainMethod = newMethod(VOID, "main", array(STRING))
        .methodCalls(ImmutableSet.of())
        .fieldAccesses(ImmutableSet.of(
            newAccess("I", "foo", "com/d/Foo", 12)
        ))
        .build();

    final DeclaredClass rootClass = newClass("com/Root")
        .methods(methodMap(mainMethod))
        .build();

    final Artifact root = newArtifact("root", rootClass);

    final ImmutableList<Artifact> classpath = ImmutableList.of(root, d2);

    final AccessedField accessed = newAccess(INT, "foo", "com/d/Foo", 12);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(rootClass.className(), mainMethod, accessed))
        .reason("Field not found: foo")
            .category(ConflictCategory.FIELD_NOT_FOUND)
            .usedBy(root.name())
            .existsIn(d2.name())
        .build();

    assertThat(conflictChecker
        .check(root, classpath, classpath))
        .isEqualTo(ImmutableList.of(expectedConflict));
  }

  @org.junit.Test
  public void testNoConflictWithInheritedMethodCall() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(INT, "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();
    final DeclaredClass subClass = newClass("com/Sub")
        .parents(ImmutableSet.of(superClass.className()))
        .build();

    final CalledMethod methodCall = newCall(subClass, methodOnlyInSuper);
    final DeclaredMethod mainMethod = newMethod(VOID, "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .fieldAccesses(ImmutableSet.of())
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, subClass, mainClass);

    assertThat(conflictChecker.check(artifact,
        ImmutableList.of(artifact),
        ImmutableList.of(artifact)
    )).isEmpty();
  }

  @org.junit.Test
  public void testNoConflictWithCovariantReturnType() throws Exception {
    final DeclaredMethod superMethod = newMethod("Ljava/lang/CharSequence;", "foo").build();
    final DeclaredClass superClass = newClass("com/Super").methods(methodMap(superMethod)).build();

    final DeclaredMethod subMethod = newMethod("Ljava/lang/String;", "foo").build();
    final DeclaredClass subClass = newClass("com/Sub").methods(methodMap(subMethod))
        .parents(ImmutableSet.of(superClass.className()))
        .build();

    final CalledMethod methodCall = newCall(subClass, superMethod);
    final DeclaredMethod mainMethod = newMethod(VOID, "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, subClass, mainClass);

    assertThat(conflictChecker
        .check(artifact,
            ImmutableList.of(artifact),
            ImmutableList.of(artifact)
        )).isEmpty();
  }

  private static Dependency dependency(ClassTypeDescriptor className,
                                       DeclaredMethod declaredMethod, CalledMethod methodCall) {
    return new MethodDependencyBuilder()
        .fromClass(className)
        .fromMethod(declaredMethod.descriptor())
        .fromLineNumber(methodCall.lineNumber())
        .targetClass(methodCall.owner())
        .targetMethod(methodCall.descriptor())
        .build();
  }

  private static Dependency dependency(ClassTypeDescriptor className,
                                       DeclaredMethod declaredMethod, AccessedField field) {
    return new FieldDependencyBuilder()
        .fromClass(className)
        .fromMethod(declaredMethod.descriptor())
        .fromLineNumber(field.lineNumber())
        .targetClass(field.owner())
        .fieldName(field.name())
        .fieldType(field.descriptor())
        .build();
  }
}
