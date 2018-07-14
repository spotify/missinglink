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
package com.spotify.missinglink;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PrettyPrinterTest {

  @Test
  public void testPrimitives() throws Exception {
    assertEquals("int", PrettyPrinter.typeDescriptor("I"));
    assertEquals("short", PrettyPrinter.typeDescriptor("S"));
    assertEquals("long", PrettyPrinter.typeDescriptor("Z"));
    assertEquals("double", PrettyPrinter.typeDescriptor("D"));
    assertEquals("float", PrettyPrinter.typeDescriptor("F"));
    assertEquals("boolean", PrettyPrinter.typeDescriptor("B"));
    assertEquals("void", PrettyPrinter.typeDescriptor("V"));
  }

  @Test
  public void testPrimitiveArrays() throws Exception {
    assertEquals("int[]", PrettyPrinter.typeDescriptor("[I"));
    assertEquals("short[]", PrettyPrinter.typeDescriptor("[S"));
  }

  @Test
  public void testClasses() throws Exception {
    assertEquals("com.spotify.Foo", PrettyPrinter.typeDescriptor("Lcom/spotify/Foo;"));
  }
}
