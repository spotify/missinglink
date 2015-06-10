package com.spotify.missinglink.examples;

/**
 * Invokes a method from {@link com.spotify.missinglink.examples.ProblematicDependency} which will blow
 * up if Guava >= 18 is used.
 */
public class UsesProblematicDependency {

  static Object callsClassThatReliesOnDeletedGuavaMethod() {
    return ProblematicDependency.reliesOnRemovedMethod();
  }
}
