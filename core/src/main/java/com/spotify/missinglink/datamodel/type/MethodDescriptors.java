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

import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.Type;

public final class MethodDescriptors {
  private static final MethodDescriptor STATIC_INIT = new MethodDescriptorBuilder()
      .returnType(TypeDescriptors.fromRaw("V"))
      .isStatic(true)
      .name("<clinit>")
      .parameterTypes(ImmutableList.of())
      .build();
  private static final Map<MethodKey, MethodDescriptor> methodDescriptorCache = new HashMap<>();

  private MethodDescriptors() {
  }

  public static MethodDescriptor fromDesc(String desc, String name, boolean isStatic) {
    final MethodKey key = new MethodKey(name, desc, isStatic);
    return methodDescriptorCache.computeIfAbsent(key, MethodDescriptors::newDescriptor);
  }

  public static MethodDescriptor staticInit() {
    return STATIC_INIT;
  }

  private static MethodDescriptor newDescriptor(MethodKey key) {
    Type type = Type.getMethodType(key.desc);

    List<TypeDescriptor> params = Arrays.stream(type.getArgumentTypes())
        .map(Type::getDescriptor)
        .map(TypeDescriptors::fromRaw)
        .collect(toList());

    return new MethodDescriptorBuilder()
        .returnType(TypeDescriptors.fromRaw(type.getReturnType().getDescriptor()))
        .isStatic(key.isStatic)
        .name(key.name)
        .parameterTypes(ImmutableList.copyOf(params))
        .build();
  }

  private static class MethodKey {
    private final String name;
    private final String desc;
    private final boolean isStatic;

    private MethodKey(String name, String desc, boolean isStatic) {
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
      MethodKey methodKey = (MethodKey) o;
      return isStatic == methodKey.isStatic &&
          Objects.equals(name, methodKey.name) &&
          Objects.equals(desc, methodKey.desc);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, desc, isStatic);
    }
  }
}
