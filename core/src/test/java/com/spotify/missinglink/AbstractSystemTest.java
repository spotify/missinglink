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

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.DeclaredClass;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class AbstractSystemTest {
  public static final Artifact DEPENDENCY_ARTIFACT = buildArtifact(MissingConstructor.class);
  public static final List<Artifact> BOOT_ARTIFACTS = buildBootArtifact();

  private static List<Artifact> buildBootArtifact() {
    List<String> paths = Splitter.on(':').splitToList(System.getProperty("sun.boot.class.path"));
    return paths.stream()
            .filter(s -> s.endsWith("/rt.jar"))
            .map(AbstractSystemTest::buildArtifact)
            .collect(Collectors.toList());
  }

  private static Artifact buildArtifact(Class clazz) {
    ProtectionDomain protectionDomain = clazz.getProtectionDomain();
    CodeSource codeSource = protectionDomain.getCodeSource();
    URL location = codeSource.getLocation();
    String path = location.getPath();
    return buildArtifact(path);
  }

  private static Artifact buildArtifact(String path) {
    try {
      return new ArtifactLoader().load(new File(path));
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  protected ImmutableList<Conflict> test(Class<?> testClass, Class<? extends Throwable> error, int expectedConflicts) throws Exception {
    try {
      invoke(testClass);
      assertEquals(error, null);
    } catch (Throwable e) {
      if (!error.equals(e.getClass())) {
        e.printStackTrace();
      }
      assertEquals(error, e.getClass());
    }
    ImmutableList<Conflict> conflicts = getConflicts(testClass);
    conflicts.forEach(System.out::println);
    assertEquals(expectedConflicts, conflicts.size());
    return conflicts;
  }

  private ImmutableList<Conflict> getConflicts(Class<?> testClass) throws IOException {
    String path = testClass.getProtectionDomain().getCodeSource().getLocation().getPath();
    String seedClassPath = path + testClass.getCanonicalName().replace('.', '/') + ".class";

    DeclaredClass declaredClass = ClassLoader.load(new File(seedClassPath));

    Artifact projectArtifact = new ArtifactBuilder()
            .name(new ArtifactName(testClass.getSimpleName()))
            .classes(ImmutableMap.of(declaredClass.className(), declaredClass))
            .build();

    ConflictChecker conflictChecker = new ConflictChecker();
    ImmutableList<Artifact> allArtifacts = ImmutableList.<Artifact>builder()
            .add(projectArtifact)
            .add(DEPENDENCY_ARTIFACT)
            .addAll(BOOT_ARTIFACTS)
            .build();
    return conflictChecker.check(
            projectArtifact,
            ImmutableList.of(projectArtifact),
            allArtifacts);
  }

  private void invoke(Class<?> testClass) throws Throwable {
    final Method method;
    try {
      method = testClass.getMethod("run");
    } catch (NoSuchMethodException e) {
      throw Throwables.propagate(e);
    }
    try {
      method.invoke(null);
    } catch (IllegalAccessException e) {
      throw Throwables.propagate(e);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
