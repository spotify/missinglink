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

import com.google.common.collect.ImmutableSet;
import com.spotify.missinglink.datamodel.access.FieldAccess;
import com.spotify.missinglink.datamodel.access.MethodCall;
import com.spotify.missinglink.datamodel.type.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptors;
import io.norberg.automatter.AutoMatter;

@AutoMatter
/** Represents methods in a class */
public interface DeclaredMethod {
  ClassTypeDescriptor owner();
  MethodDescriptor descriptor();
  int lineNumber();
  /** Methods that could be called when this method is called */
  ImmutableSet<MethodCall> methodCalls();
  /** Fields that could be accessed when this method is called */
  ImmutableSet<FieldAccess> fieldAccesses();

  default String pretty() {
    return descriptor().pretty(owner());
  }

  static DeclaredMethod of(
    ClassTypeDescriptor owner,
    MethodDescriptor descriptor,
    int lineNumber,
    ImmutableSet<MethodCall> methodCalls,
    ImmutableSet<FieldAccess> fieldAccesses
  ) {
    return new DeclaredMethodBuilder()
        .owner(owner)
        .descriptor(descriptor)
        .lineNumber(lineNumber)
        .methodCalls(methodCalls)
        .fieldAccesses(fieldAccesses)
        .build();
  }

  static DeclaredMethod emptyStaticInit(ClassTypeDescriptor owner) {
    return of(
        owner,
        MethodDescriptors.staticInit(),
        -1,
        ImmutableSet.of(),
        ImmutableSet.of());
  }

  static DeclaredMethodBuilder emptyStaticInitBuilder(ClassTypeDescriptor owner) {
    return new DeclaredMethodBuilder()
        .owner(owner)
        .descriptor(MethodDescriptors.staticInit())
        .lineNumber(-1)
        .methodCalls(ImmutableSet.of())
        .fieldAccesses(ImmutableSet.of());
  }

}
