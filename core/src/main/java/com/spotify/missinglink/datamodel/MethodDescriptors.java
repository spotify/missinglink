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

import com.google.common.collect.ImmutableList;

import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

public final class MethodDescriptors {

  private MethodDescriptors() {
  }

  public static MethodDescriptor fromDesc(String desc, String name) {
    Type type = Type.getMethodType(desc);

    List<TypeDescriptor> params = Arrays.stream(type.getArgumentTypes())
        .map(Type::getDescriptor)
        .map(TypeDescriptors::fromRaw)
        .collect(toList());

    return new MethodDescriptorBuilder()
        .returnType(TypeDescriptors.fromRaw(type.getReturnType().getDescriptor()))
        .name(name)
        .parameterTypes(ImmutableList.copyOf(params))
        .build();
  }
}
