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

import com.google.common.collect.ImmutableList;
import com.spotify.missinglink.InstanceCache;
import com.spotify.missinglink.datamodel.type.MethodDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptorBuilder;
import org.junit.Test;
import org.objectweb.asm.Opcodes;

public class MethodDescriptorsTest {
  private final InstanceCache cache = new InstanceCache();

  @Test
  public void testMethodDescriptionParser() {
    MethodDescriptor desc1 =
        cache.methodFromDesc("([I[[Lfoo/Bar;Z)V", "baz", Opcodes.ACC_STATIC);

    MethodDescriptor desc2 = new MethodDescriptorBuilder()
        .returnType(cache.typeFromRaw("V"))
        .parameterTypes(ImmutableList.of(
            cache.typeFromRaw("[I"),
            cache.typeFromRaw("[[Lfoo/Bar;"),
            cache.typeFromRaw("Z")))
        .name("baz")
        .isStatic(true)
        .build();
    assertEquals("Method descriptors should be identical", desc1, desc2);
  }
}
