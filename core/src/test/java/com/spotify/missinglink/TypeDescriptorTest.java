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
package com.spotify.missinglink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.spotify.missinglink.datamodel.ArrayTypeDescriptor;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.PrimitiveTypeDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptors;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Map;
import org.junit.Test;

public class TypeDescriptorTest {

  @Test
  public void testEquality() {
    String[] signatures = {
      "B",
      "S",
      "I",
      "J",
      "F",
      "D",
      "Z",
      "C",
      "[D",
      "[[D",
      "[[[D",
      "LFoo;",
      "LBar;",
      "[LFoo;",
      "Lfoo/bar/Baz;"
    };
    for (String signature1 : signatures) {
      for (String signature2 : signatures) {
        if (signature1.equals(signature2)) {
          assertEquals(TypeDescriptors.fromRaw(signature1), TypeDescriptors.fromRaw(signature2));
          assertEquals(
              TypeDescriptors.fromRaw(signature1).hashCode(),
              TypeDescriptors.fromRaw(signature2).hashCode());
        } else {
          assertNotEquals(TypeDescriptors.fromRaw(signature1), TypeDescriptors.fromRaw(signature2));
        }
      }
    }
  }

  @Test
  public void testDescriptions() {
    Map<String, String> desc = new HashMap<>();
    desc.put("B", "byte");
    desc.put("S", "short");
    desc.put("I", "int");
    desc.put("J", "long");
    desc.put("F", "float");
    desc.put("D", "double");
    desc.put("Z", "boolean");
    desc.put("C", "char");
    desc.put("[D", "double[]");
    desc.put("[[D", "double[][]");
    desc.put("[[[D", "double[][][]");
    desc.put("LFoo;", "Foo");
    desc.put("[LFoo;", "Foo[]");
    desc.put("[[LFoo;", "Foo[][]");
    desc.put("Lfoo/bar/Baz;", "foo.bar.Baz");

    for (Map.Entry<String, String> entry : desc.entrySet()) {
      assertEquals(entry.getValue(), TypeDescriptors.fromRaw(entry.getKey()).toString());
    }
  }

  @Test
  public void testTypes() {
    Map<String, Class> desc = new HashMap<>();
    desc.put("B", PrimitiveTypeDescriptor.class);
    desc.put("S", PrimitiveTypeDescriptor.class);
    desc.put("I", PrimitiveTypeDescriptor.class);
    desc.put("J", PrimitiveTypeDescriptor.class);
    desc.put("F", PrimitiveTypeDescriptor.class);
    desc.put("D", PrimitiveTypeDescriptor.class);
    desc.put("Z", PrimitiveTypeDescriptor.class);
    desc.put("C", PrimitiveTypeDescriptor.class);
    desc.put("[D", ArrayTypeDescriptor.class);
    desc.put("LFoo;", ClassTypeDescriptor.class);
    desc.put("[LFoo;", ArrayTypeDescriptor.class);
    desc.put("Lfoo/bar/Baz;", ClassTypeDescriptor.class);

    for (Map.Entry<String, Class> entry : desc.entrySet()) {
      assertEquals(entry.getValue(), TypeDescriptors.fromRaw(entry.getKey()).getClass());
    }
  }

  @Test(expected = InputMismatchException.class)
  public void testInvalid() {
    TypeDescriptors.fromRaw("X");
  }

  @Test(expected = InputMismatchException.class)
  public void testMoarInvalid() {
    TypeDescriptors.fromRaw("LFoo");
  }

  @Test(expected = InputMismatchException.class)
  public void testMoastInvalid() {
    TypeDescriptors.fromClassName("LFoo;");
  }

  @Test(expected = InputMismatchException.class)
  public void testMoarDifferentInvalid() {
    TypeDescriptors.fromRaw("JJ");
  }

  @Test
  public void testCanonicalNames() throws Exception {
    final TypeDescriptor expected = TypeDescriptors.fromClassName("foo.Bar");
    final TypeDescriptor actual = TypeDescriptors.fromClassName("foo/Bar");
    assertEquals(expected, actual);
  }

  @Test
  public void testNewClassTypeDescriptor() throws Exception {
    final ClassTypeDescriptor a = TypeDescriptors.fromClassName("foo.Bar");
    final ClassTypeDescriptor b = TypeDescriptors.fromClassName("foo/Bar");
    assertEquals(a, b);
  }
}
