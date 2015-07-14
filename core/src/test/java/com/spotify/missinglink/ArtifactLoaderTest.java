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

import com.spotify.missinglink.datamodel.AccessedField;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.CalledMethod;
import com.spotify.missinglink.datamodel.CalledMethodBuilder;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredField;
import com.spotify.missinglink.datamodel.DeclaredFieldBuilder;
import com.spotify.missinglink.datamodel.DeclaredMethod;
import com.spotify.missinglink.datamodel.MethodDescriptor;
import com.spotify.missinglink.datamodel.MethodDescriptorBuilder;
import com.spotify.missinglink.datamodel.TypeDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptors;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ArtifactLoaderTest {

  private Artifact artifact;
  private MethodDescriptor methodOneDescriptor;
  private MethodDescriptor printlnDescriptor;
  private MethodDescriptor internalStaticFieldAccessDescriptor;
  private MethodDescriptor internalFieldAccessDescriptor;

  private final ArtifactLoader loader = new ArtifactLoader();

  @Before
  public void setUp() throws IOException {
    artifact = loader.load(FilePathHelper.getPath("src/test/resources/ArtifactLoaderTest.jar"));

    methodOneDescriptor = new MethodDescriptorBuilder()
        .returnType(TypeDescriptors.fromRaw("V"))
        .name("methodOne")
        .parameterTypes(ImmutableList.of(TypeDescriptors.fromRaw("Ljava/lang/String;")))
        .build();

    internalStaticFieldAccessDescriptor = new MethodDescriptorBuilder()
        .returnType(TypeDescriptors.fromRaw("V"))
        .name("internalStaticFieldAccess")
        .parameterTypes(ImmutableList.of())
        .build();

    internalFieldAccessDescriptor = new MethodDescriptorBuilder()
        .returnType(TypeDescriptors.fromRaw("V"))
        .name("internalFieldAccess")
        .parameterTypes(ImmutableList.of())
        .build();

    printlnDescriptor = new MethodDescriptorBuilder()
        .returnType(TypeDescriptors.fromRaw("V"))
        .name("println")
        .parameterTypes(ImmutableList.of(TypeDescriptors.fromRaw(
                "Ljava/lang/String;")))
        .build();
  }

  /**
   * verify that the DeclaredClass.loadedClasses() set is actually populated with
   * ClassTypeDescriptor instances - other types might leak through due to asm's use of raw lists.
   */
  @Test
  @SuppressWarnings("rawtypes")
  public void testTypeOfClassParentsWhenInterfaces() throws IOException {
    final Artifact artifact = loadTestClassesAsArtifact();

    final DeclaredClass classThatImplementsInterfaces = getDeclaredClass(artifact,
        "com/spotify/missinglink/ArtifactLoaderTest$ExampleClassWithInterfaces");

    Set parents = classThatImplementsInterfaces.parents();
    for (Object key : parents) {
      assertThat(key).isInstanceOf(ClassTypeDescriptor.class);
    }
  }

  @SuppressWarnings("unused")
  private static class ExampleClassWithInterfaces implements Serializable, Cloneable {
    // no fields needed, used by testTypeOfClassParentsWhenInterfaces above
  }

  @Test
  public void testLoadClass() throws Exception {
    assertNotNull("Artifact must contain class 'A'",
        artifact.classes().get(TypeDescriptors.fromClassName("A")));
  }

  @Test
  public void testLoadMethod() throws Exception {
    assertTrue("Class must contain method with hairy signature", artifact.classes().get(
        TypeDescriptors.fromClassName("A")).methods().containsKey(methodOneDescriptor));
  }

  @Test
  public void testLoadCall() throws Exception {
    final DeclaredClass declaredClass = artifact.classes().get(TypeDescriptors.fromClassName("A"));
    DeclaredMethod method = declaredClass.methods().get(methodOneDescriptor);
    CalledMethod call = new CalledMethodBuilder()
        .owner(TypeDescriptors.fromClassName("java/io/PrintStream"))
        .lineNumber(15)
        .isStatic(false)
        .isVirtual(true)
        .descriptor(printlnDescriptor).build();
    assertTrue("Method must contain call to other method with hairy signature",
        method.methodCalls().contains(call));
  }

  @Test
  public void testLoadField() throws Exception {
    DeclaredClass loadedClass = artifact.classes().get(TypeDescriptors.fromClassName("A"));
    DeclaredField myField = new DeclaredFieldBuilder().name("publicFieldOne")
        .descriptor(TypeDescriptors.fromRaw("Ljava/lang/Object;")).build();
    assertTrue("Class must contain field with hairy signature",
        loadedClass.fields().contains(myField));
  }

  @Test
  public void testLoadStaticFieldAccess() throws Exception {
    DeclaredMethod method = artifact.classes().get(TypeDescriptors.fromClassName("A")).methods()
        .get(internalStaticFieldAccessDescriptor);
    AccessedField access = Simple.newAccess("Ljava/lang/Object;", "staticFieldOne", "A", 11);
    assertTrue("Method must contain access to staticFieldOne: " + method.fieldAccesses()
               + " does not contain " + access, method.fieldAccesses().contains(access));
  }

  @Test
  public void testLoadFieldAccess() throws Exception {
    DeclaredMethod method = artifact.classes().get(TypeDescriptors.fromClassName("A")).methods()
        .get(internalFieldAccessDescriptor);
    AccessedField access = Simple.newAccess("Ljava/lang/Object;", "publicFieldOne", "A", 12);
    assertTrue("Method must contain access to staticFieldOne: " + method.fieldAccesses()
               + " does not contain " + access, method.fieldAccesses().contains(access));
  }

  @Test
  public void testLoadParent() throws Exception {
    assertEquals(artifact.classes().get(TypeDescriptors.fromClassName("A")).parents(),
                 ImmutableSet.of(TypeDescriptors.fromClassName("java/lang/Object")));
  }

  /**
   * Verify that the asm jar can be loaded without exceptions by ArtifactLoader.
   * <p>
   * This test caught bugs where ArtifactLoader treated MethodSignature as the thing that was
   * unique
   * within a classfile, whereas the actually unique thing is the MethodDescriptor (combining the
   * ReturnDescriptor and ParameterDescriptor list).
   * </p>
   */
  @Test
  public void testLoadAsmJar() throws Exception {
    final Artifact artifact = loadAsmJar();

    // asm.jar is known to have 25 classes in it
    assertThat(artifact.classes()).hasSize(25);
  }

  private Artifact loadAsmJar() throws IOException {
    return loader.load(FilePathHelper.getPath("src/test/resources/asm-5.0.4.jar"));
  }

  /**
   * Attempt to find rt.jar from the Java installation on the classpath, and verify it can be
   * loaded
   * by ArtifactLoader without exception
   */
  @Test
  public void testLoadRtJar() throws Exception {
    final String classPath = System.getProperty("java.class.path");

    final Collection<File> files =
        Arrays.stream(classPath.split(System.getProperty("path.separator")))
            .map(File::new)
                // TODO 6/2/15 mbrown -- is every Java installation required to have a rt.jar?
                // maybe just parse all jars on the classpath instead?
            .filter(file -> file.isFile() && file.getName().equals("rt.jar"))
            .collect(Collectors.toList());

    Assume.assumeTrue(
        "Cannot test that rt.jar from classpath can be loaded because rt.jar could not be found",
        !files.isEmpty());

    for (File file : files) {
      final Artifact artifact = loader.load(file);
      assertThat(artifact.classes()).isNotEmpty();
    }
  }

  @Test
  public void testLoadFromDirectory() throws Exception {
    final Artifact artifact = loader.load(FilePathHelper.getPath("target/classes"));
    assertThat(artifact.classes())
        .overridingErrorMessage("Loading classes from a directory should be supported")
        .isNotEmpty()
            // test that a class known to be in this directory exists in the map
        .containsKey(TypeDescriptors.fromClassName(MethodDescriptor.class.getName()));
  }

  @Test
  public void testNestedClassesNamedConsistenly() throws Exception {
    final Artifact artifact = loadTestClassesAsArtifact();

    final DeclaredClass theClass = getDeclaredClass(artifact,
        "com/spotify/missinglink/nested/ClassWithNestedClass");

    final MethodDescriptor fooMethodDescriptor = theClass.methods().keySet().stream()
        .filter(descriptor -> descriptor.name().equals("foo"))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("foo method missing?"));

    final DeclaredMethod fooMethod = theClass.methods().get(fooMethodDescriptor);

    String nestedClassName = fooMethod.methodCalls().stream()
        .map(CalledMethod::owner)
        .map(TypeDescriptor::toString)
        .filter(name -> name.contains("NestedClass"))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("call to NestedClass.bar missing?"));

    //make sure that the classMap contains an entry for the nestedClassName
    assertThat(artifact.classes()).containsKey(TypeDescriptors.fromClassName(nestedClassName));
  }

  private DeclaredClass getDeclaredClass(Artifact artifact, String className) {
    final ClassTypeDescriptor key = TypeDescriptors.fromClassName(className);
    assertThat(artifact.classes()).containsKey(key);

    return artifact.classes().get(key);
  }

  private Artifact loadTestClassesAsArtifact() throws IOException {
    return loader.load(FilePathHelper.getPath("target/test-classes"));
  }

}
