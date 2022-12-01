/*-
 * -\-\-
 * missinglink-maven-plugin
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
package com.spotify.missinglink.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.spotify.missinglink.ArtifactLoader;
import com.spotify.missinglink.Conflict;
import com.spotify.missinglink.Conflict.ConflictCategory;
import com.spotify.missinglink.ConflictBuilder;
import com.spotify.missinglink.ConflictChecker;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.CalledMethod;
import com.spotify.missinglink.datamodel.CalledMethodBuilder;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredMethod;
import com.spotify.missinglink.datamodel.DeclaredMethodBuilder;
import com.spotify.missinglink.datamodel.Dependency;
import com.spotify.missinglink.datamodel.MethodDependencyBuilder;
import com.spotify.missinglink.datamodel.MethodDescriptorBuilder;
import com.spotify.missinglink.datamodel.TypeDescriptors;
import java.io.File;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * A note on the maven plugin testing harness:
 *
 * <p>The MojoRule class (and AbstractMojoTestCase) use pom.xml files in the src/test/project folder
 * to test constructing and invoking an instance of a plugin from the Maven pom.xml format.
 *
 * <p>However these pom.xml files used for testing *the Mojo* do not load a real MavenProject -
 * nothing in the pom.xml files used for testing is used to construct a real project, i.e. there is
 * no dependency resolution, etc.
 *
 * <p>So inside the CheckDependencyConflictsMojo class, calls to things like project.getArtifacts()
 * to look at the resolved transitive dependencies will always return an empty list.
 *
 * <p>The only real value of testing the Mojo using this method is to test how the fields of the
 * Mojo are set based on the POM XML file.
 */
public class CheckMojoTest {

  @Rule public MojoRule rule = new MojoRule();

  @Rule public TestResources resources = new TestResources();

  private final ArtifactLoader artifactLoader = mock(ArtifactLoader.class);
  private final ConflictChecker conflictChecker = mock(ConflictChecker.class);

  @Before
  public void setUp() throws Exception {
    // default behavior for mocks

    final Artifact emptyArtifact =
        new ArtifactBuilder()
            .name(new ArtifactName("empty"))
            .classes(ImmutableMap.<ClassTypeDescriptor, DeclaredClass>of())
            .build();
    when(artifactLoader.load(any(File.class))).thenReturn(emptyArtifact);

    when(artifactLoader.load(any(ArtifactName.class), any(File.class)))
        .thenAnswer(
            invocation ->
                ArtifactBuilder.from(emptyArtifact)
                    .name((ArtifactName) invocation.getArguments()[0])
                    .build());

    setMockConflictResults(ImmutableList.of());
  }

  private void setMockConflictResults(ImmutableList<Conflict> results) {
    when(conflictChecker.check(any(Artifact.class), anyList(), anyList())).thenReturn(results);
  }

  private CheckMojo getMojo(String dirName) throws Exception {
    final File basedir = resources.getBasedir(dirName);
    final CheckMojo mojo = (CheckMojo) rule.lookupConfiguredMojo(basedir, "check");
    mojo.artifactLoader = artifactLoader;
    mojo.conflictChecker = conflictChecker;
    return mojo;
  }

  private static Conflict conflict(
      ConflictCategory category,
      ClassTypeDescriptor inClass,
      DeclaredMethod caller,
      CalledMethod callee,
      String reason) {

    final Dependency dep =
        new MethodDependencyBuilder()
            .fromClass(inClass)
            .fromMethod(caller.descriptor())
            .fromLineNumber(callee.lineNumber())
            .targetClass(callee.owner())
            .targetMethod(callee.descriptor())
            .build();

    return new ConflictBuilder()
        .category(category)
        .dependency(dep)
        .reason(reason)
        .usedBy(new ArtifactName("source"))
        .existsIn(ConflictChecker.UNKNOWN_ARTIFACT_NAME)
        .build();
  }

  private ImmutableList<Conflict> mockConflicts(
      ConflictCategory category, ConflictCategory... additionalCategories) {
    final ClassTypeDescriptor ctd = TypeDescriptors.fromClassName("com/foo/Whatever");
    return mockConflicts(ctd, category, additionalCategories);
  }

  private ImmutableList<Conflict> mockConflicts(
      ClassTypeDescriptor ctd,
      ConflictCategory category,
      ConflictCategory... additionalCategories) {

    final CalledMethod callee =
        new CalledMethodBuilder()
            .owner(TypeDescriptors.fromClassName("com/foo/Bar"))
            .descriptor(
                new MethodDescriptorBuilder()
                    .returnType(TypeDescriptors.fromRaw("Ljava/lang/String;"))
                    .name("bat")
                    .parameterTypes(ImmutableList.of())
                    .build())
            .build();

    final DeclaredMethod caller =
        new DeclaredMethodBuilder()
            .methodCalls(ImmutableSet.of())
            .fieldAccesses(ImmutableSet.of())
            .descriptor(
                new MethodDescriptorBuilder()
                    .returnType(TypeDescriptors.fromRaw("Ljava/lang/String;"))
                    .name("bat")
                    .parameterTypes(ImmutableList.of(TypeDescriptors.fromRaw("I")))
                    .build())
            .build();

    ImmutableList.Builder<Conflict> builder = ImmutableList.builder();
    builder.add(conflict(category, ctd, caller, callee, "reasons!"));

    for (ConflictCategory cat : additionalCategories) {
      builder.add(conflict(cat, ctd, caller, callee, "more reasons!"));
    }
    return builder.build();
  }

  /** Simple test that nothing fails when no conflicts are found. */
  @Test
  public void noConflictsFound() throws Exception {
    getMojo("simple-test").execute();
  }

  @Test
  public void doesNotFailOnConflictByDefault() throws Exception {
    final CheckMojo mojo = getMojo("simple-test");

    setMockConflictResults(mockConflicts(ConflictCategory.CLASS_NOT_FOUND));

    mojo.execute();
  }

  /**
   * Tests that when {@link CheckMojo#failOnConflicts} is set to true, then the build is failed if
   * ConflictChecker returns conflicts.
   */
  @Test
  public void failsOnConflictWhenOptionIsSet() throws Exception {
    setMockConflictResults(
        mockConflicts(
            ConflictCategory.CLASS_NOT_FOUND, ConflictCategory.METHOD_SIGNATURE_NOT_FOUND));

    CheckMojo mojo = getMojo("fail-on-warning");

    MojoFailureException ex = assertThrows(MojoFailureException.class, mojo::execute);

    assertThat(ex.getMessage()).contains("conflicts found");
  }

  /**
   * The plugin should fail, and not attempt to check conflicts, if the includeCategories
   * configuration has bad values in it.
   */
  @Test
  public void testBadValuesForIncludeCategories() throws Exception {
    when(conflictChecker.check(any(Artifact.class), anyList(), anyList()))
        .thenThrow(
            new RuntimeException(
                "Mojo should not get as far as checking conflicts if the "
                    + "configuration is bad!"));

    CheckMojo mojo = getMojo("include-categories-bad-values");

    MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);

    assertThat(ex.getMessage()).contains("Invalid value(s) for 'includeCategories'");
  }

  /**
   * Tests that failOnConflicts=true does not cause a build failure if the only ConflictCategories
   * are those outside of 'includeCategories'
   */
  @Test
  public void testIncludeCategoriesActuallyFilters() throws Exception {
    setMockConflictResults(mockConflicts(ConflictCategory.METHOD_SIGNATURE_NOT_FOUND));

    // build should not fail:
    getMojo("include-categories").execute();
  }

  @Test
  public void testExcludeArtifacts() throws Exception {

    final CheckMojo mojo = getMojo("exclude-dependencies");

    // inject some dependencies to exclude later
    mojo.project.setArtifacts(
        ImmutableSet.of(
            new DefaultArtifact("com.foobar", "bizbat", "1.2.3", "compile", "jar", "", null)));

    mojo.execute();

    ArgumentCaptor<ImmutableList> toCheck = ArgumentCaptor.forClass(ImmutableList.class);

    verify(conflictChecker).check(any(Artifact.class), toCheck.capture(), anyList());

    assertThat(toCheck.getValue()).isEmpty();
  }

  /**
   * Test that an instance of the mojo configured with failOnConflicts=true does not actually fail
   * when the conflict found is in one of the ignoreSourcePackages.
   */
  @Test
  public void testIgnoreSourcePackages() throws Exception {
    final CheckMojo mojo = getMojo("ignore-source-packages");

    // make sure that the XML config is deserializing into the Mojo as expected - will catch
    // errors in an easier fashion than build failures from .execute() below
    assertThat(mojo.ignoreSourcePackages)
        .hasSize(2)
        .contains(
            new PackageFilter("groovy.lang", true),
            new PackageFilter("org.codehaus.janino", false));

    setMockConflictResults(
        mockConflicts(
            TypeDescriptors.fromClassName("groovy.lang.foo.Bar"),
            ConflictCategory.CLASS_NOT_FOUND));

    mojo.execute();
  }

  @Test
  public void testIgnoreDestinationPackages() throws Exception {
    final CalledMethod callee =
        new CalledMethodBuilder()
            .owner(TypeDescriptors.fromClassName("com/foo/Bar"))
            .descriptor(
                new MethodDescriptorBuilder()
                    .returnType(TypeDescriptors.fromRaw("Ljava/lang/String;"))
                    .name("bat")
                    .parameterTypes(ImmutableList.of())
                    .build())
            .build();

    final DeclaredMethod caller =
        new DeclaredMethodBuilder()
            .methodCalls(ImmutableSet.of())
            .fieldAccesses(ImmutableSet.of())
            .descriptor(
                new MethodDescriptorBuilder()
                    .returnType(TypeDescriptors.fromRaw("Ljava/lang/String;"))
                    .name("bat")
                    .parameterTypes(ImmutableList.of(TypeDescriptors.fromRaw("I")))
                    .build())
            .build();

    // a conflict from com/Whatever => com/foo/Bar.bat where the latter class cannot be found
    setMockConflictResults(
        ImmutableList.of(
            conflict(
                ConflictCategory.CLASS_NOT_FOUND,
                TypeDescriptors.fromClassName("com/Whatever"),
                caller,
                callee,
                "class com/foo/Bar not found")));

    final CheckMojo mojo = getMojo("ignore-destination-packages");

    // make sure that the XML config is deserializing into the Mojo as expected - will catch
    // errors in an easier fashion than build failures from .execute() below
    assertThat(mojo.ignoreDestinationPackages)
        .hasSize(1)
        .contains(new PackageFilter("com.foo", true));

    mojo.execute();
  }

  @Test
  public void testAllowSourcePackages() throws Exception {
    final CheckMojo mojo = getMojo("allow-source-packages");

    // make sure that the XML config is deserializing into the Mojo as expected - will catch
    // errors in an easier fashion than build failures from .execute() below
    assertThat(mojo.allowSourcePackages)
        .hasSize(1)
        .contains(new PackageFilter("com.foobar", false));

    setMockConflictResults(
        mockConflicts(
            TypeDescriptors.fromClassName("groovy.lang.foo.Bar"),
            ConflictCategory.CLASS_NOT_FOUND));

    mojo.execute();
  }

  @Test
  public void testAllowDestinationPackages() throws Exception {
    final CalledMethod callee =
        new CalledMethodBuilder()
            .owner(TypeDescriptors.fromClassName("com/foo/Bar"))
            .descriptor(
                new MethodDescriptorBuilder()
                    .returnType(TypeDescriptors.fromRaw("Ljava/lang/String;"))
                    .name("bat")
                    .parameterTypes(ImmutableList.of())
                    .build())
            .build();

    final DeclaredMethod caller =
        new DeclaredMethodBuilder()
            .methodCalls(ImmutableSet.of())
            .fieldAccesses(ImmutableSet.of())
            .descriptor(
                new MethodDescriptorBuilder()
                    .returnType(TypeDescriptors.fromRaw("Ljava/lang/String;"))
                    .name("bat")
                    .parameterTypes(ImmutableList.of(TypeDescriptors.fromRaw("I")))
                    .build())
            .build();

    // a conflict from com/Whatever => com/foo/Bar.bat where the latter class cannot be found
    setMockConflictResults(
        ImmutableList.of(
            conflict(
                ConflictCategory.CLASS_NOT_FOUND,
                TypeDescriptors.fromClassName("com/Whatever"),
                caller,
                callee,
                "class com/foo/Bar not found")));

    final CheckMojo mojo = getMojo("allow-destination-packages");

    // make sure that the XML config is deserializing into the Mojo as expected - will catch
    // errors in an easier fashion than build failures from .execute() below
    assertThat(mojo.allowDestinationPackages)
        .hasSize(1)
        .contains(new PackageFilter("com.foobar", true));

    mojo.execute();
  }

  @Test
  public void testCustomBootstrapPath() throws Exception {
    // need to use an existing path as the Mojo tests if the path is valid
    final String testPath = File.createTempFile("boot", ".jar").getAbsolutePath();

    final CheckMojo mojo = getMojo("simple-test");
    mojo.bootClasspath = testPath;

    mojo.execute();

    verify(artifactLoader).load(new File(testPath));
  }

  @Test
  public void testSkip() throws Exception {
    getMojo("skip").execute();

    verifyNoMoreInteractions(conflictChecker, artifactLoader);
  }

  @Test
  public void shouldIgnoreProvidedByDefault() throws Exception {
    CheckMojo mojo = getMojo("simple-test");

    setupProvidedArtifact(mojo);

    mojo.execute();

    verify(artifactLoader, never()).load(argThat(hasId("boobaz")), any(File.class));
  }

  @Test
  public void shouldCheckProvidedIfConfigured() throws Exception {
    CheckMojo mojo = getMojo("simple-test");

    setupProvidedArtifact(mojo);

    mojo.includeScopes.add(Scope.provided);

    mojo.execute();

    verify(artifactLoader).load(argThat(hasId("boobaz")), any(File.class));
  }

  private void setupProvidedArtifact(CheckMojo mojo) {
    ImmutableSet<org.apache.maven.artifact.Artifact> artifacts =
        ImmutableSet.of(
            new DefaultArtifact("com.foobar", "bizbat", "1.2.3", "compile", "jar", "", null),
            new DefaultArtifact("com.foobar", "boobaz", "1.2.3", "provided", "jar", "", null));

    // use '.' as the file so as to pretend that the artifacts are class file dependencies
    artifacts.stream().forEach(artifact -> artifact.setFile(new File(".")));

    mojo.project.setArtifacts(artifacts);
  }

  private Matcher<ArtifactName> hasId(String artifactId) {
    return new TypeSafeMatcher<ArtifactName>() {
      @Override
      protected boolean matchesSafely(ArtifactName item) {
        return item.name().contains(artifactId);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("artifact with id '" + artifactId + "'");
      }
    };
  }
}
