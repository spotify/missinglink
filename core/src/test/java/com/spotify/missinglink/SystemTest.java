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

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SystemTest extends AbstractSystemTest {

  @Test
  public void testMissingClass() throws Exception {
    ImmutableList<Conflict> conflicts = test(MissingClassTest.class, NoClassDefFoundError.class, 1);
    assertTrue(conflicts.get(0).reason().contains("Class not found: MissingClass"));
  }

  @Test
  public void testMissingConstructor() throws Exception {
    test(MissingConstructorTest.class, NoSuchMethodError.class, 2);
  }

}
