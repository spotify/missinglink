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

import com.spotify.missinglink.datamodel.Artifact;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ClassLoadingUtil {

  private static final ArtifactLoader artifactLoader = new ArtifactLoader();
  private static final AtomicReference<List<Artifact>> bootstrapArtifacts =
      new AtomicReference<>();

  public static FileInputStream findClass(Class<?> aClass) throws Exception {
    final String name = aClass.getName().replace('.', '/') + ".class";
    final File outputDir = FilePathHelper.getPath("target/test-classes");
    List<File> files = Files.walk(outputDir.toPath())
            .map(Path::toFile)
            .filter(file -> file.isFile() && file.getAbsolutePath().endsWith(name))
            .collect(Collectors.toList());
    if (files.isEmpty()) {
      throw new IllegalStateException("no file matching " + aClass + " found in "
              + outputDir + " ?");
    }
    if (files.size() >= 2) {
      throw new IllegalStateException("too many files matching " + aClass + " found in "
              + outputDir + ": " + files);
    }
    return new FileInputStream(files.get(0));
  }

  public static List<Artifact> bootstrapArtifacts() {
    if (bootstrapArtifacts.get() == null) {

      String bootstrapClasspath = System.getProperty("sun.boot.class.path");

      if (bootstrapClasspath != null) {
        List<Artifact> artifacts = constructArtifacts(Arrays.asList(
            bootstrapClasspath.split(System.getProperty("path.separator"))))
            .stream()
            .filter(c -> !c.name().name().equals("test-classes"))
            .collect(Collectors.toList());
        bootstrapArtifacts.set(artifacts);
      } else {
        List<Artifact> artifacts = Java9ModuleLoader
            .getJava9ModuleArtifacts((s, ex) -> ex.printStackTrace());
        bootstrapArtifacts.set(artifacts);
      }
    }
    return bootstrapArtifacts.get();
  }

  private static List<Artifact> constructArtifacts(Iterable<String> entries) {
    final List<Artifact> list = StreamSupport.stream(entries.spliterator(), false)
        // don't inspect paths that don't exist.
        // some bootclasspath entries, like sunrsasign.jar, are reported even if they
        // don't exist on disk - ¯\_(ツ)_/¯
        .distinct()
        .filter(ClassLoadingUtil::filterValidClasspathEntries)
        .map(ClassLoadingUtil::filepathToArtifact)
        .collect(Collectors.toList());
    return list;
  }

  private static boolean filterValidClasspathEntries(String element) {
    return filterValid(new File(element));
  }

  private static boolean filterValid(File file) {
    if (file == null) {
      return false;
    }
    final boolean isJarFile = file.isFile() && file.getName().endsWith(".jar");
    final boolean isClassDirectory = file.isDirectory();
    return isClassDirectory || isJarFile;
  }

  private static Artifact filepathToArtifact(String path) {
    try {
      return artifactLoader.load(new File(path));
    } catch (IOException e) {
      throw new MissingLinkException("failed to load path: " + path, e);
    }
  }
}
