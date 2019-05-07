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

/** Information about a method called by a DeclaredMethod */
@AutoMatter
public interface CalledMethod {

  /** Owning class of method being called */
  ClassTypeDescriptor owner();

  MethodDescriptor descriptor();

  boolean isStatic();

  int lineNumber();

  /** List of exception classes that protect this method call **/
  List<ClassTypeDescriptor> caughtExceptions();

  default String pretty() {
    return owner().toString() + "." + descriptor().prettyWithoutReturnType();
  }
}
