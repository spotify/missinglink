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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.stream.Collectors;

import io.norberg.automatter.AutoMatter;

@AutoMatter
public interface MethodDescriptor {

  TypeDescriptor returnType();

  String name();

  ImmutableList<TypeDescriptor> parameterTypes();

  // TODO: add modifiers

  default String pretty() {
    return returnType().toString() + " " + name() + prettyParameters();
  }

  default String prettyWithoutReturnType() {
    return name() + prettyParameters();
  }

  default String prettyParameters() {
    return "(" +
           Joiner.on(", ").join(parameterTypes().stream()
                                    .map(TypeDescriptor::toString)
                                    .collect(Collectors.toList())) + ")";
  }
}
