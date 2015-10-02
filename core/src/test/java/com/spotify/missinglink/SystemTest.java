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

import com.google.common.base.Throwables;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SystemTest {

  /**
   * Pick a class that's guaranteed to exist in the runtime - it doesn't matter which one.
   */
  public static final String
      RUNTIME_DEPENDENCY_PATH =
      MissingConstructor.class.getProtectionDomain().getCodeSource().getLocation().getPath();

  @Test
  public void testMissingClass() throws Exception {
    test(MissingClassTest.class, NoClassDefFoundError.class);
  }

  @Test
  public void testMissingConstructor() throws Exception {
    test(MissingConstructorTest.class, NoSuchMethodError.class);
  }

  private void test(Class<?> testClass, Class<? extends Throwable> error) throws Exception {
    final String seedClassPath = testClass.getProtectionDomain().getCodeSource().getLocation().getPath() + testClass.getCanonicalName().replace('.', '/') + ".class";
    final String runtimeDependencyPath = RUNTIME_DEPENDENCY_PATH;

    System.out.println("Should run missing link with " + seedClassPath + " and " + runtimeDependencyPath);

    try {
      invoke(testClass);
      assertEquals(error, null);
      // TODO: verify that missinglink finds no error
    } catch (Throwable e) {
      if (!error.equals(e.getClass())) {
        e.printStackTrace();
      }
      assertEquals(error, e.getClass());
      // TODO: verify that missinglink finds exactly one error
    }
  }

  private void invoke(Class<?> testClass) throws Throwable {
    final Method method;
    try {
      method = testClass.getMethod("run");
    } catch (NoSuchMethodException e) {
      throw Throwables.propagate(e);
    }
    try {
      method.invoke(null);
    } catch (IllegalAccessException e) {
      throw Throwables.propagate(e);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
