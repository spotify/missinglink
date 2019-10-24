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

import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.Dependency;

import io.norberg.automatter.AutoMatter;

@AutoMatter
public interface Conflict {
  Dependency dependency();

  ArtifactName existsIn();

  ArtifactName usedBy();

  ConflictCategory category();

  String reason();

  default String describe() {
    return category().name() + "\n  " + reason() + "\n  " + dependency().describe() + "\n  used by " + usedBy()  + " (exists in " + existsIn() + ")";
  }

  enum ConflictCategory {
    CLASS_NOT_FOUND,
    METHOD_SIGNATURE_NOT_FOUND,
    FIELD_NOT_FOUND
  }
}
