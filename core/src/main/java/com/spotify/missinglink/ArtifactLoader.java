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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap.Builder;

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
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.util.stream.Collectors.toList;

public class ArtifactLoader {

  /** Load artifact at path, using path name as artifactId */
  public Artifact load(File path) throws IOException {
    return load(new ArtifactName(path.getName()), path);
  }

  /** Load artifact at path with a custom artifactId */
  public Artifact load(ArtifactName artifactName, File path) throws IOException {
    Preconditions.checkArgument(path.exists(), "Path must exist: " + path);
    Preconditions.checkArgument(path.isFile() || path.isDirectory(),
        "Path must be a file or directory: " + path);

    if (path.isFile()) {
      return loadFromJar(artifactName, path);
    }
    return loadFromDirectory(artifactName, path);
  }

  private Artifact loadFromJar(ArtifactName artifactName, File path) {
    try (JarFile jarFile = new JarFile(path)) {
      Builder<ClassTypeDescriptor, DeclaredClass> classes = new Builder<>();
      // Why would anyone bother updating this API to add support for iterators? Way too much work..
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class")) {
          try {
            DeclaredClass cl = ClassLoader.load(jarFile.getInputStream(entry));
            classes.put(cl.className(), cl);
          } catch (MissingLinkException e) {
            throw e;
          } catch (Exception e) {
            throw new MissingLinkException("Could not load " + entry.getName() + " from " + path,
                                           e);
          }
        }
      }
      return artifact(artifactName, classes);
    } catch (IOException e) {
      throw new RuntimeException("Could not load " + path, e);
    }
  }

  private Artifact loadFromDirectory(ArtifactName artifactName, File dir) throws IOException {
    Builder<ClassTypeDescriptor, DeclaredClass> classes =
        new Builder<>();

    List<File> classFilesInDir = Files.walk(dir.toPath())
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

  private static Artifact artifact(ArtifactName name,
                                   Builder<ClassTypeDescriptor, DeclaredClass> classes) {
    return new ArtifactBuilder()
        .name(name)
        .classes(classes.build())
        .build();
  }

  public static void main(String[] args) throws Exception {
    ArtifactLoader l = new ArtifactLoader();
    System.out.println(l.load(new File("core/src/test/resources/ArtifactLoaderTest.jar")));
  }

}
