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
  private static final AtomicReference<ImmutableList<Artifact>> bootstrapArtifacts =
      new AtomicReference<>();

  public static FileInputStream findClass(Class<?> aClass) throws Exception {
    final File outputDir = FilePathHelper.getPath("target/test-classes");
    File someClass = Files.walk(outputDir.toPath())
        .map(Path::toFile)
        .filter(file -> file.isFile() && file.getName().endsWith(aClass.getSimpleName() + ".class"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no file matching " + aClass + " found in " +
                                                     outputDir + " ?"));

    return new FileInputStream(someClass);
  }

  public static ImmutableList<Artifact> bootstrapArtifacts() {
    if (bootstrapArtifacts.get() == null) {
      String bootstrapClasspath = System.getProperty("sun.boot.class.path");

      ImmutableList<Artifact> artifacts = constructArtifacts(Arrays.asList(
          bootstrapClasspath.split(System.getProperty("path.separator"))));

      bootstrapArtifacts.set(artifacts);
    }
    return bootstrapArtifacts.get();
  }

  private static ImmutableList<Artifact> constructArtifacts(Iterable<String> entries) {
    final List<Artifact> list = StreamSupport.stream(entries.spliterator(), false)
        // don't inspect paths that don't exist.
        // some bootclasspath entries, like sunrsasign.jar, are reported even if they
        // don't exist on disk - ¯\_(ツ)_/¯
        .distinct()
        .filter(ClassLoadingUtil::filterValidClasspathEntries)
        .map(ClassLoadingUtil::filepathToArtifact)
        .collect(Collectors.toList());
    return ImmutableList.copyOf(list);
  }

  public static boolean filterValidClasspathEntries(String element) {
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
