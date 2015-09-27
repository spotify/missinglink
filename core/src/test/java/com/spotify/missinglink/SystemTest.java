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

import org.junit.Test;

import static org.junit.Assert.fail;

public class SystemTest {

  @Test
  public void testMissingClass() throws Exception {
    try {
      MissingClassTest.main();
      fail();
    } catch (NoClassDefFoundError e) {
      ConflictFinder conflictFinder = new ConflictFinder();
      MissingClassTest.class.getProtectionDomain().getCodeSource().
      getClass().getClassLoader().
      conflictFinder.addSeed()
      conflictFinder.findConflict();
      e.printStackTrace();
    }
  }

  @Test
  public void testMissingConstructor() throws Exception {
    try {
      MissingConstructorTest.main();
      fail();
    } catch (NoSuchMethodError e) {
      e.printStackTrace();
    }
  }
}
