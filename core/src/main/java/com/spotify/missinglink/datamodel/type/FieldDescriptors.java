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
package com.spotify.missinglink.datamodel.type;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FieldDescriptors {

  private FieldDescriptors() {
  }

  public static FieldDescriptor fromDesc(String desc, String name, boolean isStatic) {
    final FieldKey key = new FieldKey(name, desc, isStatic);
    return fieldDescriptorCache.computeIfAbsent(key, FieldDescriptors::newDescriptor);
  }

  private static FieldDescriptor newDescriptor(FieldKey key) {
    return new FieldDescriptorBuilder()
        .isStatic(key.isStatic)
        .fieldType(TypeDescriptors.fromRaw(key.desc))
        .name(key.name)
        .build();
  }

  private static final Map<FieldKey, FieldDescriptor> fieldDescriptorCache = new HashMap<>();

  private static class FieldKey {
    private final String name;
    private final String desc;
    private final boolean isStatic;

    private FieldKey(String name, String desc, boolean isStatic) {
      this.name = Preconditions.checkNotNull(name);
      this.desc = Preconditions.checkNotNull(desc);
      this.isStatic = isStatic;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      FieldKey fieldKey = (FieldKey) o;
      return isStatic == fieldKey.isStatic &&
          Objects.equals(name, fieldKey.name) &&
          Objects.equals(desc, fieldKey.desc);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, desc, isStatic);
    }
  }
}
