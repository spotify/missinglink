/*
 * Copyright (c) 2019 Spotify AB
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
package com.spotify.missinglink.dependencies;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.lang.System.getProperty;

class ArtifactCache {
  private final File dir;

  private ArtifactCache(File dir) {
    this.dir = dir;
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        throw new RuntimeException("Could not create directory: " + dir);
      }
    }
    if (!dir.isDirectory()) {
      throw new RuntimeException("Expected a directory: " + dir);
    }
  }

  static ArtifactCache getDefault() {
    String homeDir = getProperty("user.home");
    File root = new File(homeDir + "/.m2/repository/dependency-data");
    return new ArtifactCache(root);
  }

  ArtifactContainer resolve(
          Resolver resolver, Coordinate coordinate,
          Callable<ArtifactContainer> fallback) {
    if (coordinate.isSnapshot()) {
      return invoke(fallback);
    }
    String filename = coordinate.toString().replace(':', '_') + ".json.gz";
    File file = new File(dir, filename);
    try {
      if (file.exists()) {
        JSONObject data = getObject(file);
        IncompleteArtifact artifactContainer = JsonReader.fromJson(data);
        Set<ArtifactContainer> dependencies = artifactContainer.getDependencies().stream()
                .map(resolver::resolve)
                .collect(Collectors.toSet());

        return artifactContainer.complete(dependencies);
      } else {
        ArtifactContainer artifactContainer = invoke(fallback);
        JSONObject jsonObject = JsonWriter.toJsonObject(artifactContainer);
        writeObject(file, jsonObject);
        return artifactContainer;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ArtifactContainer invoke(Callable<ArtifactContainer> fallback) {
    try {
      return fallback.call();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void writeObject(File file, JSONObject object) throws IOException {
    try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(
            new FileOutputStream(file)), StandardCharsets.UTF_8)) {
      object.write(writer, 2, 0);
    }
  }

  private JSONObject getObject(File file) throws IOException {
    try (Reader reader = new InputStreamReader(new GZIPInputStream(
            new FileInputStream(file)), StandardCharsets.UTF_8)) {
      return new JSONObject(new JSONTokener(reader));
    }
  }
}
