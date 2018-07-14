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
package com.spotify.missinglink.datamodel.state;

import com.spotify.missinglink.datamodel.type.FieldDescriptor;
import io.norberg.automatter.AutoMatter;

@AutoMatter
/** Represents field in a class */
public interface DeclaredField {
  FieldDescriptor descriptor();

  static DeclaredField of(
      FieldDescriptor descriptor
  ) {
    return new DeclaredFieldBuilder()
        .descriptor(descriptor)
        .build();
  }
}
