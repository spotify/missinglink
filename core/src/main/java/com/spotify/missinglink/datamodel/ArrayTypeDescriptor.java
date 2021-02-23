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


import java.util.Objects;

public class ArrayTypeDescriptor implements TypeDescriptor {

  private final TypeDescriptor subType;
  private final int dimensions;

  public ArrayTypeDescriptor(TypeDescriptor subType, int dimensions) {
    this.dimensions = dimensions;
    this.subType = Objects.requireNonNull(subType);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(subType.toString());
    for (int i = 0; i < dimensions; i++) {
      sb.append("[]");
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ArrayTypeDescriptor that = (ArrayTypeDescriptor) o;

    return subType.equals(that.subType) && dimensions == that.dimensions;
  }

  @Override
  public int hashCode() {
    return subType.hashCode();
  }
}
