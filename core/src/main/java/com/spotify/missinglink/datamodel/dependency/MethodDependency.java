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
package com.spotify.missinglink.datamodel.dependency;

import com.spotify.missinglink.datamodel.access.MethodCall;
import com.spotify.missinglink.datamodel.type.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptor;
import io.norberg.automatter.AutoMatter;

/**
 * Represents a dependency between a method and the method that it calls, used in Conflict when
 * reporting problems
 */
@AutoMatter
public interface MethodDependency extends Dependency {
  ClassTypeDescriptor fromOwner();
  MethodDescriptor fromMethod();
  int fromLineNumber();

  MethodCall methodCall();

  @Override
  default ClassTypeDescriptor targetClass() {
    return methodCall().owner();
  }

  @Override
  default String describe() {
    return "Call to: " + methodCall().pretty();
  }

  static MethodDependency of(
      ClassTypeDescriptor fromOwner, MethodDescriptor fromMethod, int fromLineNumber,
      MethodCall methodCall) {
    return new MethodDependencyBuilder()
        .fromOwner(fromOwner)
        .fromMethod(fromMethod)
        .fromLineNumber(fromLineNumber)
        .methodCall(methodCall)
        .build();
  }
}
