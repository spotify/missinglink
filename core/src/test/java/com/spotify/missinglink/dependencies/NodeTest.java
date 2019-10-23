/*
 * Copyright (c) 2019 Spotify AB
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
package com.spotify.missinglink.dependencies;

import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

public class NodeTest {
  @Test
    public void testSimple() {
      Map<String, Set<String>> input = map1("java.lang.String", Collections.singleton("first"));
      Map<String, Set<String>> output = Node.getDependencyMap(input);
      assertEquals(map1("**", Collections.singleton("first")), output);
    }

    @Test
    public void testMerged() {
      Map<String, Set<String>> input = map2(
              "java.lang.String", Collections.singleton("first"),
              "java.lang.Object", Collections.singleton("first")
      );
      Map<String, Set<String>> output = Node.getDependencyMap(input);
      assertEquals(map1("**", Collections.singleton("first")), output);
    }

    @Test
    public void testMergedPackage() {
      Map<String, Set<String>> input = map3(
              "java.lang.String", Collections.singleton("first"),
              "java.lang.Object", Collections.singleton("first"),
              "java.util.Date", Collections.singleton("second")
      );
      Map<String, Set<String>> output = Node.getDependencyMap(input);
      assertEquals(map2(
              "java.lang.**", Collections.singleton("first"),
              "java.util.**", Collections.singleton("second")
      ), output);
    }

    @Test
    public void testSplitClasses() {
      Map<String, Set<String>> input = map3(
              "java.lang.String", Collections.singleton("first"),
              "java.lang.Object", Collections.singleton("first"),
              "java.lang.util.Something", Collections.singleton("second")
      );
      Map<String, Set<String>> output = Node.getDependencyMap(input);
      assertEquals(map2(
              "java.lang.*", Collections.singleton("first"),
              "java.lang.util.**", Collections.singleton("second")
      ), output);
    }

    @Test
    public void testCantMerge() {
      Map<String, Set<String>> input = map2(
              "java.lang.String", Collections.singleton("first"),
              "java.lang.Object", Collections.singleton("second")
      );
      Map<String, Set<String>> output = Node.getDependencyMap(input);
      assertEquals(input, output);
    }

    @Test
    public void testNoPackageMerge() {
      Map<String, Set<String>> input = map2(
              "String", Collections.singleton("first"),
              "Object", Collections.singleton("first")
      );
      Map<String, Set<String>> output = Node.getDependencyMap(input);
      assertEquals(map1("**", Collections.singleton("first")), output);
    }

    @Test
    public void testNoPackageDontMerge() {
      Map<String, Set<String>> input = map3(
              "String", Collections.singleton("first"),
              "Object", Collections.singleton("first"),
              "java.Something", Collections.singleton("second")
      );
      Map<String, Set<String>> output = Node.getDependencyMap(input);
      Map<String, Set<String>> expected = map2(
              "*", Collections.singleton("first"),
              "java.**", Collections.singleton("second"));
      assertEquals(expected, output);
    }

  static Map<String, Set<String>> map1(String k, Set<String> v) {
    Map<String, Set<String>> map = new TreeMap<>();
    map.put(k, v);
    return map;
  }

  static Map<String, Set<String>> map2(String k, Set<String> v, String k2, Set<String> v2) {
    Map<String, Set<String>> map = new TreeMap<>();
    map.put(k, v);
    map.put(k2, v2);
    return map;
  }

  static Map<String, Set<String>> map3(
          String k, Set<String> v,
          String k2, Set<String> v2,
          String k3, Set<String> v3) {
    Map<String, Set<String>> map = new TreeMap<>();
    map.put(k, v);
    map.put(k2, v2);
    map.put(k3, v3);
    return map;
  }
}
