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

import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.TypeDescriptors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.spotify.missinglink.ClassLoadingUtil.findClass;
import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderTest {

  private FileInputStream inputStream;

  @Before
  public void setUp() throws Exception {
    final File outputDir = FilePathHelper.getPath("target/classes");
    File someClass = Files.walk(outputDir.toPath())
        .map(Path::toFile)
        .filter(file -> file.isFile() && file.getName().endsWith(".class"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no classfiles in " + outputDir + " ?"));

    inputStream = new FileInputStream(someClass);
  }

  @After
  public void tearDown() throws Exception {
    inputStream.close();
  }

  /** Simple test that load() doesn't blow up */
  @Test
  public void testLoad() throws Exception {
    final DeclaredClass declaredClass = ClassLoader.load(inputStream);
    assertThat(declaredClass).isNotNull();
    assertThat(declaredClass.methods()).isNotEmpty();
  }

  @Test
  public void shouldHandleLoadingOfType() throws Exception {
    try (FileInputStream inputStream = findClass(LdcLoadType.class)) {
      DeclaredClass loaded = ClassLoader.load(inputStream);

      assertThat(loaded.className().getClassName()).contains("LdcLoadType");
    }
  }

  @Test
  public void shouldHandleLoadingOfArrayOfType() throws Exception {
    try (FileInputStream inputStream = findClass(LdcLoadArrayOfType.class)) {
      DeclaredClass loaded = ClassLoader.load(inputStream);

      assertThat(loaded.className().getClassName()).contains("LdcLoadArrayOfType");
      assertThat(loaded.loadedClasses())
          .containsExactly(TypeDescriptors.fromClassName(Object.class.getName()));
    }
  }

  @Test
  public void shouldHandleLoadingOfArrayOfPrimitive() throws Exception {
    try (FileInputStream inputStream = findClass(LdcLoadArrayOfPrimitive.class)) {
      DeclaredClass loaded = ClassLoader.load(inputStream);

      assertThat(loaded.className().getClassName()).contains("LdcLoadArrayOfPrimitive");
      assertThat(loaded.loadedClasses()).isEmpty();
    }
  }

  static class LdcLoadType {
    static void test() {
      System.out.println(FileInputStream.class.toString());
    }
  }

  static class LdcLoadArrayOfType {
    static void test() {
      System.out.println(Object[].class);
    }
  }

  static class LdcLoadArrayOfPrimitive {
    static void test() {
      System.out.println(long[].class);
    }
  }
}
