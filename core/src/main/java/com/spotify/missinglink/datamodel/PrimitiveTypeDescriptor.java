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
package com.spotify.missinglink.datamodel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum PrimitiveTypeDescriptor implements TypeDescriptor {
  BYTE('B', "byte"),
  SHORT('S', "short"),
  INT('I', "int"),
  LONG('J', "long"),
  FLOAT('F', "float"),
  DOUBLE('D', "double"),
  BOOLEAN('Z', "boolean"),
  CHAR('C', "char");

  private final char raw;
  private final String pretty;

  PrimitiveTypeDescriptor(char raw, String pretty) {
    this.raw = raw;
    this.pretty = pretty;
  }

  @Override
  public String toString() {
    return pretty;
  }

  public String getRaw() {
    return Character.toString(raw);
  }

  private static final Map<String, PrimitiveTypeDescriptor> mapping = createMapping();

  private static Map<String, PrimitiveTypeDescriptor> createMapping() {
    final Map<String, PrimitiveTypeDescriptor> map = new HashMap<>();
    for (PrimitiveTypeDescriptor type : PrimitiveTypeDescriptor.values()) {
      map.put(Character.toString(type.raw), type);
    }
    return Collections.unmodifiableMap(map);
  }

  public static PrimitiveTypeDescriptor fromRaw(String typeDescriptor) {
    final PrimitiveTypeDescriptor descriptor = mapping.get(typeDescriptor);
    if (descriptor == null) {
      return null;
    }
    return descriptor;
  }
}
