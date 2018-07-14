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

import com.google.common.collect.ImmutableList;
import io.norberg.automatter.AutoMatter;
import java.util.stream.Collectors;
import org.objectweb.asm.Opcodes;

@AutoMatter
public interface MethodDescriptor {
  TypeDescriptor returnType();
  boolean isStatic();
  String name();
  ImmutableList<TypeDescriptor> parameterTypes();

  default String pretty() {
    return (isStatic() ? "static " : "") +
        returnType().toString() + " " + name() + prettyParameters();
  }

  default String pretty(ClassTypeDescriptor owner) {
    return (isStatic() ? "static " : "") + returnType().toString() + " " +
        owner.getClassName() + "." + name() + prettyParameters();
  }

  default String prettyWithoutReturnType() {
    return name() + prettyParameters();
  }

  default String prettyParameters() {
    return "(" +
        parameterTypes().stream()
            .map(TypeDescriptor::pretty)
            .collect(Collectors.joining(", ")) + ")";
  }

  static MethodDescriptor fromDesc(String desc, String name, int access) {
    return fromDesc(desc, name, (access & Opcodes.ACC_STATIC) != 0);
  }

  static MethodDescriptor fromDesc(String desc, String name, boolean isStatic) {
    return MethodDescriptors.fromDesc(desc, name, isStatic);
  }

  static MethodDescriptor staticInit() {
    return MethodDescriptors.staticInit();
  }
}
