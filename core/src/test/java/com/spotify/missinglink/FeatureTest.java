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

import static com.spotify.missinglink.ClassLoader.load;
import static com.spotify.missinglink.ClassLoadingUtil.findClass;
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
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.spotify.missinglink.Conflict.ConflictCategory;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.access.FieldAccess;
import com.spotify.missinglink.datamodel.access.MethodCall;
import com.spotify.missinglink.datamodel.access.MethodCallBuilder;
import com.spotify.missinglink.datamodel.dependency.Dependency;
import com.spotify.missinglink.datamodel.dependency.FieldDependencyBuilder;
import com.spotify.missinglink.datamodel.dependency.MethodDependencyBuilder;
import com.spotify.missinglink.datamodel.state.DeclaredClass;
import com.spotify.missinglink.datamodel.state.DeclaredMethod;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class FeatureTest {

  private final ConflictChecker conflictChecker = new ConflictChecker();

  @org.junit.Test
  public void testSimpleConflict() throws Exception {

    final DeclaredMethod methodOnlyInD1 = newMethod(false, INT, "com/d/Foo", "foo").build();
    final DeclaredClass fooClass = newClass("com/d/Foo").methods(methodMap(methodOnlyInD1)).build();

    final Artifact d2 = newArtifact("empty");

    final MethodCall methodCall = newCall(methodOnlyInD1, false, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "com/Root", "main")
        .methodCalls(ImmutableSet.of(methodCall)).build();

    final DeclaredClass rootClass = newClass("com/Root")
        .methods(methodMap(mainMethod))
        .build();

    final Artifact root = newArtifact("root", rootClass);

    final ImmutableList<Artifact> classpath = ImmutableList.of(root, d2);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(mainMethod, methodCall))
        .reason("Class not found: com.d.Foo")
        .category(ConflictCategory.CLASS_NOT_FOUND)
        .usedBy(root.name())
        .existsIn(ConflictChecker.UNKNOWN_ARTIFACT_NAME)
        .build();

    assertThat(conflictChecker
                   .check(ImmutableList.of(root), classpath, classpath))
        .isEqualTo(ImmutableList.of(expectedConflict));
  }

  @org.junit.Test
  public void testSimpleConflict2() throws Exception {

    final DeclaredMethod methodOnlyInD1 = newMethod(false, INT, "com/d/Foo", "foo").build();
    final DeclaredClass fooClass = newClass("com/d/Foo")
        .methods(methodMap(methodOnlyInD1))
        .build();

    final DeclaredClass d2Class = newClass("com/d/Foo").build();
    final Artifact d2 = newArtifact("D2", d2Class);

    final MethodCall methodCall = newCall(methodOnlyInD1, false, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "com/Root", "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .build();

    final DeclaredClass rootClass = newClass("com/Root")
        .methods(methodMap(mainMethod))
        .build();

    final Artifact root = newArtifact("root", rootClass);

    final ImmutableList<Artifact> classpath = ImmutableList.of(root, d2);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(mainMethod, methodCall))
        .reason("Method not found: int com.d.Foo.foo()")
        .category(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND)
        .usedBy(root.name())
        .existsIn(d2.name())
        .build();

    assertThat(conflictChecker
                   .check(ImmutableList.of(root), classpath, classpath))
        .isEqualTo(ImmutableList.of(expectedConflict));
  }

  @org.junit.Test
  public void testMissingField() throws Exception {

    final DeclaredClass d2Class = newClass("com/d/Foo").build();
    final Artifact d2 = newArtifact("D2", d2Class);

    final DeclaredMethod mainMethod = newMethod(true, VOID, "com/Root", "main", array(STRING))
        .methodCalls(ImmutableSet.of())
        .fieldAccesses(ImmutableSet.of(
            newAccess("I", "foo", "com/d/Foo", false, 12)
        ))
        .build();

    final DeclaredClass rootClass = newClass("com/Root")
        .methods(methodMap(mainMethod))
        .build();

    final Artifact root = newArtifact("root", rootClass);

    final ImmutableList<Artifact> classpath = ImmutableList.of(root, d2);

    final FieldAccess accessed = newAccess(INT, "foo", "com/d/Foo", false, 12);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(mainMethod, accessed))
        .reason("Field not found: int com.d.Foo.foo")
        .category(ConflictCategory.FIELD_NOT_FOUND)
        .usedBy(root.name())
        .existsIn(d2.name())
        .build();

    assertThat(conflictChecker
                   .check(ImmutableList.of(root), classpath, classpath))
        .isEqualTo(ImmutableList.of(expectedConflict));
  }

  @org.junit.Test
  public void testNoConflictWithInheritedMethodCall() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(false, INT, "com/super", "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();
    final DeclaredClass subClass = newClass("com/Sub")
        .parents(ImmutableSet.of(superClass.className()))
        .build();

    final MethodCall methodCall = newCall(methodOnlyInSuper, false, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "com/Main", "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .fieldAccesses(ImmutableSet.of())
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, subClass, mainClass);

    assertThat(conflictChecker.check(ImmutableList.of(artifact),
                                     ImmutableList.of(artifact),
                                     ImmutableList.of(artifact)
    )).isEmpty();
  }

  @org.junit.Test
  public void testNoConflictWithCovariantReturnType() throws Exception {
    final DeclaredMethod superMethod =
        newMethod(false, "Ljava/lang/CharSequence;", "com/Super", "foo").build();
    final DeclaredClass superClass = newClass("com/Super").methods(methodMap(superMethod)).build();

    final DeclaredMethod subMethod =
        newMethod(false, "Ljava/lang/String;", "com/Sub", "foo").build();
    final DeclaredClass subClass = newClass("com/Sub").methods(methodMap(subMethod))
        .parents(ImmutableSet.of(superClass.className()))
        .build();

    final MethodCall methodCall = newCall(superMethod, false, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "com/Main", "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, subClass, mainClass);

    assertThat(conflictChecker
                   .check(ImmutableList.of(artifact),
                          ImmutableList.of(artifact),
                          ImmutableList.of(artifact)
                   )).isEmpty();
  }

  @org.junit.Test
  public void testNoConflictWithStaticCall() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(true, INT, "com/super", "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();

    final MethodCall methodCall = newCall(methodOnlyInSuper, true, false);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "com/Main", "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .fieldAccesses(ImmutableSet.of())
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, mainClass);

    assertThat(conflictChecker.check(ImmutableList.of(artifact),
                                     ImmutableList.of(artifact),
                                     ImmutableList.of(artifact)
    )).isEmpty();
  }

  @org.junit.Test
  public void testConflictWithStaticToVirtualCall() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(false, INT, "com/super", "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();

    final MethodCall methodCall = newCall(newMethod(true, INT, "com/super", "foo").build());
    final DeclaredMethod mainMethod = newMethod(true, VOID, "com/Main", "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .fieldAccesses(ImmutableSet.of())
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, mainClass);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(mainMethod, methodCall))
        .reason("Method not found: static int com.super.foo()")
        .category(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND)
        .usedBy(artifact.name())
        .existsIn(artifact.name())
        .build();

    assertEquals(Arrays.asList(expectedConflict),
                 conflictChecker.check(ImmutableList.of(artifact),
                                       ImmutableList.of(artifact),
                                       ImmutableList.of(artifact)
                 ));
  }

  @org.junit.Test
  public void testConflictWithVirtualToStaticCall() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(true, INT, "com/super", "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();

    final MethodCall methodCall = newCall(newMethod(false, INT, "com/super", "foo").build());
    final DeclaredMethod mainMethod = newMethod(true, VOID, "com/Main", "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .fieldAccesses(ImmutableSet.of())
        .build();

    final DeclaredClass mainClass = newClass("com/Main")
        .parents(ImmutableSet.of(superClass.className()))
        .methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, mainClass);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(mainMethod, methodCall))
        .reason("Method not found: int com.super.foo()")
        .category(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND)
        .usedBy(artifact.name())
        .existsIn(artifact.name())
        .build();

    assertEquals(Arrays.asList(expectedConflict),
                 conflictChecker.check(ImmutableList.of(artifact),
                                       ImmutableList.of(artifact),
                                       ImmutableList.of(artifact)
                 ));
  }

  @org.junit.Test
  public void testNoConflictWithStaticCallInSuper() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(true, INT, "com/super", "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();

    final MethodCall methodCall = newCall(methodOnlyInSuper);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "com/Main", "main", array(STRING))
        .methodCalls(ImmutableSet.of(methodCall))
        .fieldAccesses(ImmutableSet.of())
        .build();

    final DeclaredClass mainClass = newClass("com/Main")
        .parents(ImmutableSet.of(superClass.className()))
        .methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, mainClass);

    assertEquals(Collections.emptyList(),
                 conflictChecker.check(ImmutableList.of(artifact),
                                       ImmutableList.of(artifact),
                                       ImmutableList.of(artifact)
                 ));
  }

  @org.junit.Test
  public void testNoConflictWithSpecialCallToSuper() throws Exception {
    class SuperDuperClass {
      boolean fie(Object o) {
        return o != null;
      }
    }

    class SuperClass extends SuperDuperClass {
      // does not define foo(Object)
    }

    class MainClass extends SuperClass {
      boolean foo(Object o) {
        return super.fie(o);
      }
    }

    DeclaredClass superDuperClass = load(findClass(SuperDuperClass.class));
    DeclaredClass superClass = load(findClass(SuperClass.class));
    DeclaredClass mainClass = load(findClass(MainClass.class));

    final Artifact artifact = newArtifact("art", superDuperClass, superClass, mainClass);

    ImmutableList<Artifact> allArtifacts = ImmutableList.<Artifact>builder()
        .addAll(ClassLoadingUtil.bootstrapArtifacts())
        .add(artifact)
        .build();

    assertThat(conflictChecker.check(ImmutableList.of(artifact),
                                     ImmutableList.of(artifact),
                                     allArtifacts)).isEmpty();
  }

  @Test
  public void shouldReportMissingParent() throws Exception {
    class LostParent {
    }

    class LacksParent extends LostParent {
    }

    DeclaredClass parent = load(findClass(LostParent.class));
    DeclaredClass mainClass = load(findClass(LacksParent.class));

    final Artifact artifact = newArtifact("art", mainClass);

    ImmutableList<Artifact> allArtifacts = ImmutableList.<Artifact>builder()
        .addAll(ClassLoadingUtil.bootstrapArtifacts())
        .add(artifact)
        .build();

    DeclaredMethod parentInit = parent.methods().values().stream()
        .filter(declaredMethod -> declaredMethod.descriptor().name().equals("<init>"))
        .findFirst()
        .get();
    DeclaredMethod init = mainClass.methods().values().stream()
        .filter(declaredMethod -> declaredMethod.descriptor().name().equals("<init>"))
        .findFirst()
        .get();

    MethodCall methodCall = new MethodCallBuilder()
        .owner(parentInit.owner())
        .descriptor(parentInit.descriptor())
        .lineNumber(init.lineNumber())
        .build();

    Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(init, methodCall))
        .reason("Class not found: com.spotify.missinglink.FeatureTest$1LostParent")
        .category(ConflictCategory.CLASS_NOT_FOUND)
        .usedBy(artifact.name())
        .existsIn(ConflictChecker.UNKNOWN_ARTIFACT_NAME)
        .build();

    assertThat(conflictChecker.check(ImmutableList.of(artifact),
                                     ImmutableList.of(artifact),
                                     allArtifacts))
        .containsExactly(expectedConflict);
  }

  private static Dependency dependency(DeclaredMethod declaredMethod, MethodCall methodCall) {
    return new MethodDependencyBuilder()
        .fromOwner(declaredMethod.owner())
        .fromMethod(declaredMethod.descriptor())
        .fromLineNumber(methodCall.lineNumber())
        .methodCall(methodCall)
        .build();
  }

  private static Dependency dependency(DeclaredMethod declaredMethod, FieldAccess field) {
    return new FieldDependencyBuilder()
        .fromOwner(declaredMethod.owner())
        .fromMethod(declaredMethod.descriptor())
        .fromLineNumber(field.lineNumber())
        .fieldAccess(field)
        .build();
  }
}
