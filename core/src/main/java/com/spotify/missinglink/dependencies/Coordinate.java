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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.json.JSONObject;

import java.util.Comparator;
import java.util.Objects;

public class Coordinate {
  private final String groupId;
  private final String artifactId;
  private final String version;
  private final PackagingType packagingType;
  private final String classifier;
  private final boolean isSnapshot;

  private Coordinate(String groupId, String artifactId, String version) {
    this(groupId, artifactId, version, PackagingType.JAR);
  }

  private Coordinate(
          String groupId, String artifactId, String version, PackagingType packagingType) {
    this(groupId, artifactId, version, packagingType, packagingType.getClassifier());
  }

  private Coordinate(
          String groupId, String artifactId, String version,
          PackagingType packagingType, String classifier) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.packagingType = packagingType;
    this.classifier = classifier;
    this.isSnapshot = version.endsWith("-SNAPSHOT");
  }

  static Coordinate fromMaven(MavenCoordinate coordinate) {
    return new Coordinate(
            coordinate.getGroupId(), coordinate.getArtifactId(), coordinate.getVersion(),
            coordinate.getPackaging(), coordinate.getClassifier());
  }

  static Coordinate fromModel(Model model) {
    PackagingType packagingType = PackagingType.of(model.getPackaging());
    String classifier = packagingType.getClassifier();
    return new Coordinate(
            model.getGroupId(), model.getArtifactId(), model.getVersion(),
            packagingType, classifier);
  }

  static Coordinate fromMaven(Dependency dependency) {
    // TODO: use packaging type or classifier?
    return new Coordinate(
            dependency.getGroupId(),
            dependency.getArtifactId(),
            dependency.getVersion());
  }

  public static Comparator<? super Coordinate> comparator() {
    return (Comparator<Coordinate>) (o1, o2) -> {
      if (!o1.getArtifactName().equals(o2.getArtifactName())) {
        throw new IllegalArgumentException("Can't compare artifacts: " + o1 + " and " + o2);
      }

      return VersionComparator.INSTANCE.compare(o1.getVersion(), o2.getVersion());
    };
  }

  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getVersion() {
    return version;
  }

  public PackagingType getPackagingType() {
    return packagingType;
  }

  public String getClassifier() {
    return classifier;
  }

  public String getArtifactName() {
    return getGroupId() + ":" + getArtifactId();
  }

  public static Coordinate fromString(String s) {
    String[] parts = s.split(":", -1);
    if (parts.length == 3) {
      return new Coordinate(parts[0], parts[1], parts[2]);
    }
    if (parts.length == 4) {
      String groupId = parts[0];
      String artifactId = parts[1];
      PackagingType packagingType = PackagingType.of(parts[2]);
      String version = parts[3];
      return new Coordinate(
              groupId, artifactId, version,
              packagingType, packagingType.getClassifier());
    }
    if (parts.length == 5) {
      String groupId = parts[0];
      String artifactId = parts[1];
      PackagingType packagingType = PackagingType.of(parts[2]);
      String classifier = parts[3];
      String version = parts[4];
      return new Coordinate(groupId, artifactId, version, packagingType, classifier);
    }
    throw new IllegalArgumentException("Not a valid coordinate: " + s);
  }

  public static Coordinate fromJson(JSONObject object) {
    String groupId = object.getString("groupId");
    String artifactId = object.getString("artifactId");
    String version = object.getString("version");
    PackagingType packagingType = PackagingType.of(object.getString("packaging_type"));
    String classifier = object.getString("classifier");
    return new Coordinate(groupId, artifactId, version, packagingType, classifier);
  }

  public JSONObject toJson() {
    JSONObject object = new JSONObject();
    object.put("groupId", groupId);
    object.put("artifactId", artifactId);
    object.put("version", version);
    object.put("packaging_type", packagingType.getId());
    object.put("classifier", classifier);
    return object;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder()
            .append(groupId)
            .append(":").append(artifactId)
            .append(":").append(packagingType.getId());
    if (!classifier.isEmpty()) {
      builder.append(":").append(classifier);
    }
    return builder.append(":").append(version).toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Coordinate that = (Coordinate) o;
    return groupId.equals(that.groupId) &&
            artifactId.equals(that.artifactId) &&
            version.equals(that.version) &&
            packagingType.equals(that.packagingType) &&
            classifier.equals(that.classifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, artifactId, version, packagingType, classifier);
  }

  public boolean isSnapshot() {
    return isSnapshot;
  }
}
