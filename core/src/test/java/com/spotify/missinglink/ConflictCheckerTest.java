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

import static org.assertj.core.api.Assertions.assertThat;

import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.CalledMethodBuilder;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClassBuilder;
import com.spotify.missinglink.datamodel.DeclaredMethodBuilder;
import com.spotify.missinglink.datamodel.MethodDescriptor;
import com.spotify.missinglink.datamodel.MethodDescriptorBuilder;
import com.spotify.missinglink.datamodel.TypeDescriptors;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ConflictCheckerTest {

  private Artifact projectArtifact;
  private Artifact rt;
  private Artifact libraryArtifact;

  @Before
  public void setUp() throws Exception {
    final MethodDescriptor cloneDescriptor =
        new MethodDescriptorBuilder()
            .name("clone")
            .returnType(TypeDescriptors.fromClassName("java/lang/Object"))
            .build();

    rt =
        new ArtifactBuilder()
            .name(new ArtifactName("rt"))
            .classes(
                Collections.singletonMap(
                    TypeDescriptors.fromClassName("java/lang/Object"),
                    new DeclaredClassBuilder()
                        .className(TypeDescriptors.fromClassName("java/lang/Object"))
                        .methods(
                            Collections.singletonMap(
                                cloneDescriptor,
                                new DeclaredMethodBuilder().descriptor(cloneDescriptor).build()))
                        // TODO: maybe add more methods here
                        .build()))
            .build();

    projectArtifact =
        new ArtifactBuilder()
            .name(new ArtifactName("foo"))
            .classes(
                Collections.singletonMap(
                    TypeDescriptors.fromClassName("com/spotify/ClassName"),
                    Simple.newClass("com/spotify/ClassName")
                        .parents(
                            Collections.singleton(
                                TypeDescriptors.fromClassName("java/lang/Object")))
                        .methods(
                            Simple.methodMap(
                                Simple.newMethod(false, Simple.OBJECT, "something")
                                    .methodCalls(
                                        Collections.singleton(
                                            new CalledMethodBuilder()
                                                .owner(
                                                    TypeDescriptors.fromClassName(
                                                        "java/lang/Object"))
                                                .descriptor(cloneDescriptor)
                                                .build()))
                                    .build()))
                        .build()))
            .build();

    ClassTypeDescriptor libClass1 = TypeDescriptors.fromClassName("org/library/ClassName");
    MethodDescriptor brokenMethodDescriptor =
        new MethodDescriptorBuilder()
            .name("broken")
            .returnType(TypeDescriptors.fromClassName("java/lang/Object"))
            .build();

    libraryArtifact =
        new ArtifactBuilder()
            .name(new ArtifactName("lib"))
            .classes(
                Collections.singletonMap(
                    libClass1,
                    new DeclaredClassBuilder()
                        .className(libClass1)
                        .parents(
                            Collections.singleton(
                                TypeDescriptors.fromClassName("java/lang/Object")))
                        .methods(
                            Simple.methodMap(
                                Simple.newMethod(false, Simple.OBJECT, "broken")
                                    .methodCalls(
                                        Collections.singleton(
                                            new CalledMethodBuilder()
                                                .owner(
                                                    TypeDescriptors.fromClassName(
                                                        "java/lang/Object"))
                                                .descriptor(brokenMethodDescriptor)
                                                .build()))
                                    .build()))
                        .build()))
            .build();
  }

  @Test
  public void shouldSupportInvocationsOnArrayTypes() throws Exception {
    ConflictChecker checker = new ConflictChecker();

    final List<Conflict> conflicts =
        checker.check(
            projectArtifact,
            Arrays.asList(projectArtifact, rt),
            Arrays.asList(projectArtifact, rt));
    assertThat(conflicts).isEmpty();
  }

  @Test
  public void shouldNotReportUnreachableClassConflicts() throws Exception {
    List<Artifact> artifacts = Arrays.asList(projectArtifact, libraryArtifact, rt);

    ConflictChecker checker = new ConflictChecker();

    assertThat(checker.check(projectArtifact, artifacts, artifacts)).isEmpty();
  }
}
