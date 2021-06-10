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

import static java.util.stream.Collectors.toList;

import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArtifactLoader {

  /** Load artifact at path, using path name as artifactId */
  public Artifact load(File path) throws IOException {
    return load(new ArtifactName(path.getName()), path);
  }

  /** Load artifact at path with a custom artifactId */
  public Artifact load(ArtifactName artifactName, File path) throws IOException {
    if (!path.exists()) {
      throw new IllegalArgumentException("Path must exist: " + path);
    }
    if (!path.isFile() && !path.isDirectory()) {
      throw new IllegalArgumentException("Path must be a file or directory: " + path);
    }
    if (path.isFile()) {
      return loadFromJar(artifactName, path);
    }
    return loadFromDirectory(artifactName, path);
  }

  // This is designed to handle Multi-Release JAR files, where there are class files for multiple
  // versions of JVM in one jar.
  // You don't want to end up trying to parse a new class file when running on an old JVM.
  // https://openjdk.java.net/jeps/238
  private Collection<JarEntry> getClassesForCurrentJavaVersion(Iterable<JarEntry> entries) {
    // First categorize all the found class files by their target JVM
    // classFilesPerJavaVersion: target JVM version -> normalized full name -> JarEntry
    SortedMap<Integer, Map<String, JarEntry>> classFilesPerJavaVersion = new TreeMap<>();
    String patternString = "META-INF/versions/(\\d+)/";
    Pattern pattern = Pattern.compile(patternString);
    for (JarEntry entry : entries) {
      String fileFullName = entry.getName();
      if (fileFullName.endsWith(".class")) {
        Matcher m = pattern.matcher(fileFullName);
        if (m.find()) {
          Integer targetJavaVersion = Integer.parseInt(m.group(1));
          String normalizedFileFullName = fileFullName.replaceAll(patternString, "");

          if (!classFilesPerJavaVersion.containsKey(targetJavaVersion)) {
            classFilesPerJavaVersion.put(targetJavaVersion, new HashMap<>());
          }
          classFilesPerJavaVersion.get(targetJavaVersion).put(normalizedFileFullName, entry);
        } else {
          Integer withoutVersion = 0;
          if (!classFilesPerJavaVersion.containsKey(withoutVersion)) {
            classFilesPerJavaVersion.put(withoutVersion, new HashMap<>());
          }
          classFilesPerJavaVersion.get(withoutVersion).put(fileFullName, entry);
        }
      }
    }

    // We have to figure out what JVM version we're running on.
    Integer currentJavaVersion;
    String[] javaVersionElements =
        System.getProperty("java.version").replaceAll("\\-ea", "").split("\\.");
    if (javaVersionElements[0].equals("1")) {
      currentJavaVersion = Integer.parseInt(javaVersionElements[1]);
    } else {
      currentJavaVersion = Integer.parseInt(javaVersionElements[0]);
    }

    // Start layering the class files from old JVM version to new and thus effectively override the
    // old files by the new ones.
    Map<String, JarEntry> selectedClassFiles = new HashMap<>();
    for (Map.Entry<Integer, Map<String, JarEntry>> entry : classFilesPerJavaVersion.entrySet()) {
      Integer targetJavaVersion = entry.getKey();
      if (targetJavaVersion > currentJavaVersion) {
        break;
      }
      Map<String, JarEntry> pathToClassfile = entry.getValue();
      selectedClassFiles.putAll(pathToClassfile);
    }

    return selectedClassFiles.values();
  }

  private Artifact loadFromJar(ArtifactName artifactName, File path) {
    try (JarFile jarFile = new JarFile(path)) {
      Map<ClassTypeDescriptor, DeclaredClass> classes = new HashMap<>();
      Iterable<JarEntry> classFiles =
          getClassesForCurrentJavaVersion(Collections.list(jarFile.entries()));
      for (JarEntry entry : classFiles) {
        try {
          DeclaredClass cl = ClassLoader.load(jarFile.getInputStream(entry));
          classes.put(cl.className(), cl);
        } catch (MissingLinkException e) {
          throw e;
        } catch (Exception e) {
          throw new MissingLinkException("Could not load " + entry.getName() + " from " + path, e);
        }
      }
      return artifact(artifactName, classes);
    } catch (IOException e) {
      throw new RuntimeException("Could not load " + path, e);
    }
  }

  private Artifact loadFromDirectory(ArtifactName artifactName, File dir) throws IOException {
    Map<ClassTypeDescriptor, DeclaredClass> classes = new HashMap<>();

    List<File> classFilesInDir =
        Files.walk(dir.toPath())
            .map(Path::toFile)
            .filter(file -> file.isFile() && file.getName().endsWith(".class"))
            .collect(toList());

    for (File file : classFilesInDir) {
      try (FileInputStream fis = new FileInputStream(file)) {
        DeclaredClass cl = ClassLoader.load(fis);
        classes.put(cl.className(), cl);
      }
    }
    return artifact(artifactName, classes);
  }

  private static Artifact artifact(
      ArtifactName name, Map<ClassTypeDescriptor, DeclaredClass> classes) {
    return new ArtifactBuilder().name(name).classes(classes).build();
  }

  public static void main(String[] args) throws Exception {
    ArtifactLoader l = new ArtifactLoader();
    System.out.println(l.load(new File("core/src/test/resources/ArtifactLoaderTest.jar")));
  }
}
