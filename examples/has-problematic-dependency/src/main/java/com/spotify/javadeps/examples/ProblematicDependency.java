package com.spotify.missinglink.examples;

import com.google.common.base.Enums;

/**
 * Calls a method in the Guava Enums class which was removed in guava 18. If a project calls this
 * method while overriding Guava to >= 18, it will cause a NoSuchMethodError at runtime.
 */
public class ProblematicDependency {

  public static Object reliesOnRemovedMethod() {
    return Enums.valueOfFunction(Foo.class);
  }

  enum Foo {
    BAR
  }
}
