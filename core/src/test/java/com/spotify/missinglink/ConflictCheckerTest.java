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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.access.MethodCallBuilder;
import com.spotify.missinglink.datamodel.state.DeclaredClassBuilder;
import com.spotify.missinglink.datamodel.state.DeclaredField;
import com.spotify.missinglink.datamodel.state.DeclaredMethodBuilder;
import com.spotify.missinglink.datamodel.type.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptorBuilder;
import com.spotify.missinglink.datamodel.type.TypeDescriptors;
import org.junit.Before;
import org.junit.Test;

public class ConflictCheckerTest {

  Artifact projectArtifact;
  Artifact rt;
  Artifact libraryArtifact;
  MethodDescriptor cloneDescriptor;

  @Before
  public void setUp() throws Exception {
    cloneDescriptor = new MethodDescriptorBuilder()
        .name("clone")
        .parameterTypes(ImmutableList.of())
        .returnType(TypeDescriptors.fromClassName("java/lang/Object"))
        .build();

    rt = new ArtifactBuilder()
        .name(new ArtifactName("rt"))
        .classes(ImmutableMap.of(
            TypeDescriptors.fromClassName("java/lang/Object"), new DeclaredClassBuilder()
                .className(TypeDescriptors.fromClassName("java/lang/Object"))
                .parents(ImmutableSet.of())
                .fields(ImmutableSet.<DeclaredField>of())
                .methods(ImmutableMap.of(
                    cloneDescriptor,
                    new DeclaredMethodBuilder()
                        .owner(TypeDescriptors.fromClassName(
                            "java/lang/Object"))
                        .descriptor(cloneDescriptor)
                        .methodCalls(ImmutableSet.of())
                        .fieldAccesses(ImmutableSet.of())
                        .build()))
                    // TODO: maybe add more methods here
                .build()))
        .build();

    projectArtifact = new ArtifactBuilder()
        .name(new ArtifactName("foo"))
        .classes(ImmutableMap.of(
            TypeDescriptors.fromClassName("com/spotify/ClassName"),
            Simple.newClass("com/spotify/ClassName")
                .parents(ImmutableSet.of(TypeDescriptors.fromClassName("java/lang/Object")))
                .methods(Simple.methodMap(
                    Simple.newMethod(false, Simple.OBJECT, "com/spotify/ClassName", "something")
                        .methodCalls(ImmutableSet.of(
                            new MethodCallBuilder()
                                .owner(TypeDescriptors.fromClassName("java/lang/Object"))
                                .descriptor(cloneDescriptor)
                                .build()))
                        .build()))
                .build()))
        .build();

    ClassTypeDescriptor libClass1 = TypeDescriptors.fromClassName("org/library/ClassName");
    MethodDescriptor brokenMethodDescriptor = new MethodDescriptorBuilder()
        .name("broken")
        .parameterTypes(ImmutableList.of())
        .returnType(TypeDescriptors.fromClassName("java/lang/Object"))
        .build();

    libraryArtifact = new ArtifactBuilder()
        .name(new ArtifactName("lib"))
        .classes(ImmutableMap.of(
            libClass1, new DeclaredClassBuilder()
                .className(libClass1)
                .parents(ImmutableSet.of(TypeDescriptors.fromClassName("java/lang/Object")))
                .fields(ImmutableSet.<DeclaredField>of())
                .methods(Simple.methodMap(
                    Simple.newMethod(false, Simple.OBJECT, libClass1.getClassName(), "broken")
                        .methodCalls(ImmutableSet.of(new MethodCallBuilder()
                            .owner(TypeDescriptors.fromClassName("java/lang/Object"))
                            .descriptor(brokenMethodDescriptor)
                            .build()))
                        .build()))
                .build()))
        .build();
  }

  @Test
  public void shouldSupportInvocationsOnArrayTypes() throws Exception {
    ConflictChecker checker = new ConflictChecker();

    final ImmutableList<Conflict> conflicts = checker.check(ImmutableList.of(projectArtifact),
        ImmutableList.of(projectArtifact, rt),
        ImmutableList.of(projectArtifact, rt)
    );
    assertThat(conflicts).isEmpty();
  }

  @Test
  public void shouldNotReportUnreachableClassConflicts() throws Exception {
    ImmutableList<Artifact> artifacts = ImmutableList.of(projectArtifact, libraryArtifact, rt);

    ConflictChecker checker = new ConflictChecker();

    assertThat(checker.check(ImmutableList.of(projectArtifact), artifacts, artifacts)).isEmpty();
  }
}
