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
import java.util.Map;
import java.util.Set;

@AutoMatter
public interface DeclaredClass {

  // names are com/foo/bar/Baz
  ClassTypeDescriptor className();

  // parent are class names: com/foo/bar/Baz
  Set<ClassTypeDescriptor> parents();
  // also includes other classes that are loaded by this class, even though
  // no methods on those classes are explicitly called
  Set<ClassTypeDescriptor> loadedClasses();

  Map<MethodDescriptor, DeclaredMethod> methods();

  Set<DeclaredField> fields();
}
