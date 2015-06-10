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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.norberg.automatter.AutoMatter;

@AutoMatter
public interface DeclaredClass {

  // names are com/foo/bar/Baz
  ClassTypeDescriptor className();

  // parent are class names: com/foo/bar/Baz
  ImmutableSet<ClassTypeDescriptor> parents();

  ImmutableMap<MethodDescriptor, DeclaredMethod> methods();

  ImmutableSet<DeclaredField> fields();

}
