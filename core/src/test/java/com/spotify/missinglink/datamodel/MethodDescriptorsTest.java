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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MethodDescriptorsTest {

  @Test
  public void testMethodDescriptionParser() {
    MethodDescriptor desc1 = MethodDescriptors.fromDesc("([I[[Lfoo/Bar;Z)V", "baz");
    MethodDescriptor desc2 =
        new MethodDescriptorBuilder()
            .returnType(TypeDescriptors.fromRaw("V"))
            .parameterTypes(
                TypeDescriptors.fromRaw("[I"),
                TypeDescriptors.fromRaw("[[Lfoo/Bar;"),
                TypeDescriptors.fromRaw("Z"))
            .name("baz")
            .build();
    assertEquals("Method descriptors should be identical", desc1, desc2);
  }

  @Test
  public void testPrettyParameters() {
    MethodDescriptor desc = MethodDescriptors.fromDesc("([I[[Lfoo/Bar;Z)V", "baz");
    assertEquals("(int[], foo.Bar[][], boolean)", desc.prettyParameters());
  }
}
