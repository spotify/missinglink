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

import io.norberg.automatter.AutoMatter;
import java.util.List;
import java.util.StringJoiner;

@AutoMatter
public interface MethodDescriptor {

  TypeDescriptor returnType();

  String name();

  List<TypeDescriptor> parameterTypes();

  // TODO: add modifiers

  default String pretty() {
    return returnType().toString() + " " + name() + prettyParameters();
  }

  default String prettyWithoutReturnType() {
    return name() + prettyParameters();
  }

  default String prettyParameters() {
    StringJoiner joiner = new StringJoiner(", ", "(", ")");

    parameterTypes().stream()
        .map(TypeDescriptor::toString)
        .forEach(joiner::add);

    return joiner.toString();
  }
}
