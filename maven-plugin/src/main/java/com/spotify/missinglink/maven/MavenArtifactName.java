/*-
 * -\-\-
 * missinglink-maven-plugin
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
package com.spotify.missinglink.maven;

import com.spotify.missinglink.datamodel.ArtifactName;

import static com.google.common.base.Preconditions.checkNotNull;

public class MavenArtifactName extends ArtifactName {

  private final String groupId;
  private final String artifactId;
  private final String version;

  public MavenArtifactName(String groupId, String artifactId, String version) {
    super(groupId + ":" + artifactId + ":" + version);
    this.groupId = checkNotNull(groupId);
    this.artifactId = checkNotNull(artifactId);
    this.version = checkNotNull(version);
  }

  String groupId() {
    return groupId;
  }

  String artifactId() {
    return artifactId;
  }

  String version() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    MavenArtifactName that = (MavenArtifactName) o;

    if (!groupId.equals(that.groupId)) {
      return false;
    }
    if (!artifactId.equals(that.artifactId)) {
      return false;
    }
    return version.equals(that.version);

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + groupId.hashCode();
    result = 31 * result + artifactId.hashCode();
    result = 31 * result + version.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "MavenArtifactName{" + "groupId='" + groupId + '\'' + ", artifactId='" + artifactId
           + '\'' + ", version='" + version + '\'' + '}';
  }
}
