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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public final class MethodDescriptors {

  private MethodDescriptors() {
  }

  public static MethodDescriptor fromDesc(String desc, String name) {
    final MethodKey key = new MethodKey(name, desc);
    return methodDescriptorCache.computeIfAbsent(key, MethodDescriptors::newDescriptor);
  }

  private static MethodDescriptor newDescriptor(MethodKey key) {
    Type type = Type.getMethodType(key.desc);

    List<TypeDescriptor> params = Arrays.stream(type.getArgumentTypes())
        .map(Type::getDescriptor)
        .map(TypeDescriptors::fromRaw)
        .collect(toList());

    return new MethodDescriptorBuilder()
        .returnType(TypeDescriptors.fromRaw(type.getReturnType().getDescriptor()))
        .name(key.name)
        .parameterTypes(ImmutableList.copyOf(params))
        .build();
  }

  private static final Map<MethodKey, MethodDescriptor> methodDescriptorCache = new HashMap<>();

  private static class MethodKey {
    private final String name;
    private final String desc;

    public MethodKey(String name, String desc) {
      this.name = Preconditions.checkNotNull(name);
      this.desc = Preconditions.checkNotNull(desc);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      MethodKey key = (MethodKey) o;

      if (!name.equals(key.name)) {
        return false;
      }
      return desc.equals(key.desc);

    }

    @Override
    public int hashCode() {
      int result = name.hashCode();
      result = 31 * result + desc.hashCode();
      return result;
    }
  }
}
