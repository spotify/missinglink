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

import com.spotify.missinglink.Conflict.ConflictCategory;
import com.spotify.missinglink.datamodel.AccessedField;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.CalledMethod;
import com.spotify.missinglink.datamodel.CalledMethodBuilder;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredClassBuilder;
import com.spotify.missinglink.datamodel.DeclaredMethod;
import com.spotify.missinglink.datamodel.Dependency;
import com.spotify.missinglink.datamodel.FieldDependencyBuilder;
import com.spotify.missinglink.datamodel.MethodDependencyBuilder;
import com.spotify.missinglink.datamodel.TypeDescriptors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class FeatureTest {

  private final ConflictChecker conflictChecker = new ConflictChecker();

  @org.junit.Test
  public void testSimpleConflict() throws Exception {

    final DeclaredMethod methodOnlyInD1 = newMethod(false, INT, "foo").build();
    final DeclaredClass fooClass = newClass("com/d/Foo").methods(methodMap(methodOnlyInD1)).build();

    final Artifact d2 = newArtifact("empty");

    final CalledMethod methodCall = newCall(fooClass, methodOnlyInD1, false, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "main")
        .methodCalls(Collections.singleton(methodCall)).build();

    final DeclaredClass rootClass = newClass("com/Root")
        .methods(methodMap(mainMethod))
        .build();

    final Artifact root = newArtifact("root", rootClass);

    final List<Artifact> classpath = Arrays.asList(root, d2);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(rootClass.className(), mainMethod, methodCall))
        .reason("Class not found: com.d.Foo")
        .category(ConflictCategory.CLASS_NOT_FOUND)
        .usedBy(root.name())
        .existsIn(ConflictChecker.UNKNOWN_ARTIFACT_NAME)
        .build();

    assertThat(conflictChecker
        .check(root, classpath, classpath))
        .isEqualTo(Collections.singletonList(expectedConflict));
  }

  @org.junit.Test
  public void testSimpleConflict2() throws Exception {

    final DeclaredMethod methodOnlyInD1 = newMethod(false, INT, "foo").build();
    final DeclaredClass fooClass = newClass("com/d/Foo")
        .methods(methodMap(methodOnlyInD1))
        .build();

    final DeclaredClass d2Class = newClass("com/d/Foo").build();
    final Artifact d2 = newArtifact("D2", d2Class);

    final CalledMethod methodCall = newCall(fooClass, methodOnlyInD1, false, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "main", array(STRING))
        .methodCalls(Collections.singleton(methodCall))
        .build();

    final DeclaredClass rootClass = newClass("com/Root")
        .methods(methodMap(mainMethod))
        .build();

    final Artifact root = newArtifact("root", rootClass);

    final List<Artifact> classpath = Arrays.asList(root, d2);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(rootClass.className(), mainMethod, methodCall))
        .reason("Method not found: com.d.Foo.foo()")
        .category(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND)
        .usedBy(root.name())
        .existsIn(d2.name())
        .build();

    assertThat(conflictChecker
        .check(root, classpath, classpath))
        .isEqualTo(Collections.singletonList(expectedConflict));
  }

  @org.junit.Test
  public void testMissingField() throws Exception {

    final DeclaredClass d2Class = newClass("com/d/Foo").build();
    final Artifact d2 = newArtifact("D2", d2Class);

    final DeclaredMethod mainMethod = newMethod(true, VOID, "main", array(STRING))
        .fieldAccesses(Collections.singleton(
            newAccess("I", "foo", "com/d/Foo", 12)
        ))
        .build();

    final DeclaredClass rootClass = newClass("com/Root")
        .methods(methodMap(mainMethod))
        .build();

    final Artifact root = newArtifact("root", rootClass);

    final List<Artifact> classpath = Arrays.asList(root, d2);

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
        .isEqualTo(Collections.singletonList(expectedConflict));
  }

  @org.junit.Test
  public void testNoConflictWithInheritedMethodCall() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(false, INT, "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();
    final DeclaredClass subClass = newClass("com/Sub")
        .parents(Collections.singleton(superClass.className()))
        .build();

    final CalledMethod methodCall = newCall(subClass, methodOnlyInSuper, false, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "main", array(STRING))
        .methodCalls(Collections.singleton(methodCall))
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, subClass, mainClass);

    assertThat(conflictChecker.check(artifact,
        Collections.singletonList(artifact),
        Collections.singletonList(artifact)
    )).isEmpty();
  }

  @org.junit.Test
  public void testNoConflictWithCovariantReturnType() throws Exception {
    final DeclaredMethod superMethod = newMethod(false, "Ljava/lang/CharSequence;", "foo").build();
    final DeclaredClass superClass = newClass("com/Super").methods(methodMap(superMethod)).build();

    final DeclaredMethod subMethod = newMethod(false, "Ljava/lang/String;", "foo").build();
    final DeclaredClass subClass = newClass("com/Sub").methods(methodMap(subMethod))
        .parents(Collections.singleton(superClass.className()))
        .build();

    final CalledMethod methodCall = newCall(subClass, superMethod, false, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "main", array(STRING))
        .methodCalls(Collections.singleton(methodCall))
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, subClass, mainClass);

    assertThat(conflictChecker
        .check(artifact,
            Collections.singletonList(artifact),
            Collections.singletonList(artifact)
        )).isEmpty();
  }

  @org.junit.Test
  public void testNoConflictWithStaticCall() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(true, INT, "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();

    final CalledMethod methodCall = newCall(superClass, methodOnlyInSuper, true, false);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "main", array(STRING))
        .methodCalls(Collections.singleton(methodCall))
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, mainClass);

    assertThat(conflictChecker.check(artifact,
        Collections.singletonList(artifact),
        Collections.singletonList(artifact)
    )).isEmpty();
  }

  @org.junit.Test
  public void testConflictWithStaticToVirtualCall() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(false, INT, "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();

    final CalledMethod methodCall = newCall(superClass, methodOnlyInSuper, true, false);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "main", array(STRING))
        .methodCalls(Collections.singleton(methodCall))
        .build();

    final DeclaredClass mainClass = newClass("com/Main").methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, mainClass);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(mainClass.className(), mainMethod, methodCall))
        .reason("Method not found: com.super.foo()")
        .category(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND)
        .usedBy(artifact.name())
        .existsIn(artifact.name())
        .build();

    assertEquals(Collections.singletonList(expectedConflict),
        conflictChecker.check(artifact,
            Collections.singletonList(artifact),
            Collections.singletonList(artifact)
        ));
  }

  @org.junit.Test
  public void testConflictWithVirtualToStaticCall() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(true, INT, "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();

    final CalledMethod methodCall = newCall(superClass, methodOnlyInSuper, false, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "main", array(STRING))
        .methodCalls(Collections.singleton(methodCall))
        .build();

    final DeclaredClass mainClass = newClass("com/Main")
        .parents(Collections.singleton(superClass.className()))
        .methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, mainClass);

    final Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(mainClass.className(), mainMethod, methodCall))
        .reason("Method not found: com.super.foo()")
        .category(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND)
        .usedBy(artifact.name())
        .existsIn(artifact.name())
        .build();

    assertEquals(Collections.singletonList(expectedConflict),
        conflictChecker.check(artifact,
            Collections.singletonList(artifact),
            Collections.singletonList(artifact)
        ));
  }

  @org.junit.Test
  public void testNoConflictWithStaticCallInSuper() throws Exception {
    final DeclaredMethod methodOnlyInSuper = newMethod(true, INT, "foo").build();
    final DeclaredClass superClass =
        newClass("com/super").methods(methodMap(methodOnlyInSuper)).build();

    ClassTypeDescriptor mainClassName = TypeDescriptors.fromClassName("com/Main");
    final CalledMethod methodCall = newCall(mainClassName, methodOnlyInSuper, true);
    final DeclaredMethod mainMethod = newMethod(true, VOID, "main", array(STRING))
        .methodCalls(Collections.singleton(methodCall))
        .build();

    final DeclaredClass mainClass = newClass("com/Main")
        .parents(Collections.singleton(superClass.className()))
        .methods(methodMap(mainMethod)).build();

    final Artifact artifact = newArtifact("art", superClass, mainClass);

    assertEquals(Collections.emptyList(),
        conflictChecker.check(artifact,
            Collections.singletonList(artifact),
            Collections.singletonList(artifact)
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

    List<Artifact> allArtifacts = new ArrayList<>(ClassLoadingUtil.bootstrapArtifacts());
    allArtifacts.add(artifact);

    assertThat(conflictChecker.check(artifact,
        Collections.singletonList(artifact),
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

    List<Artifact> allArtifacts = new ArrayList<>(ClassLoadingUtil.bootstrapArtifacts());
    allArtifacts.add(artifact);

    DeclaredMethod parentInit = parent.methods().values().stream()
        .filter(declaredMethod -> declaredMethod.descriptor().name().equals("<init>"))
        .findFirst()
        .get();
    DeclaredMethod init = mainClass.methods().values().stream()
        .filter(declaredMethod -> declaredMethod.descriptor().name().equals("<init>"))
        .findFirst()
        .get();

    CalledMethod calledMethod = new CalledMethodBuilder()
        .descriptor(parentInit.descriptor())
        .isStatic(parentInit.isStatic())
        .lineNumber(init.lineNumber())
        .owner(parent.className())
        .build();

    Conflict expectedConflict = new ConflictBuilder()
        .dependency(dependency(mainClass.className(), init, calledMethod))
        .reason("Class not found: com.spotify.missinglink.FeatureTest$1LostParent")
        .category(ConflictCategory.CLASS_NOT_FOUND)
        .usedBy(artifact.name())
        .existsIn(ConflictChecker.UNKNOWN_ARTIFACT_NAME)
        .build();

    assertThat(conflictChecker.check(artifact,
        Collections.singletonList(artifact),
        allArtifacts))

        .containsExactly(expectedConflict);
  }

  @Test
  public void shouldNotReportConflictIfCatchingNoClassDefFoundError() throws Exception {
    class MissingClass {

      public void foo() {

      }
    }

    class CatchesMissingClass {

      public void bar() {
        try {
          new MissingClass().foo();
        } catch (NoClassDefFoundError ex) {
          ex.printStackTrace();
        }
      }
    }

    DeclaredClass catcher = load(findClass(CatchesMissingClass.class));

    final Artifact artifact = newArtifact("art", catcher);

    List<Artifact> allArtifacts = new ArrayList<>(ClassLoadingUtil.bootstrapArtifacts());
    allArtifacts.add(artifact);

    assertThat(conflictChecker.check(artifact,
        Collections.singletonList(artifact),
        allArtifacts))
        .isEmpty();
  }

  @Test
  public void shouldNotReportConflictIfCatchingNoSuchMethodError() throws Exception {
    class MissingMethodClass {

      public void foo() {

      }
    }

    class CatchesMissingMethod {

      public void bar() {
        try {
          new MissingMethodClass().foo();
        } catch (NoSuchMethodError ex) {
          ex.printStackTrace();
        }
      }
    }

    DeclaredClass missingMethod = DeclaredClassBuilder
        .from(load(findClass(MissingMethodClass.class)))
        .build();

    DeclaredClass catcher = load(findClass(CatchesMissingMethod.class));

    final Artifact artifact = newArtifact("art", catcher, missingMethod);

    List<Artifact> allArtifacts = new ArrayList<>(ClassLoadingUtil.bootstrapArtifacts());
    allArtifacts.add(artifact);

    assertThat(conflictChecker.check(artifact,
        Collections.singletonList(artifact),
        allArtifacts))
        .isEmpty();
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
