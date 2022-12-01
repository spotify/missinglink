package com.spotify.missinglink;

import com.spotify.missinglink.e.E;

/**
 * This is expected to generate a runtime error because WillGoAway(), as invoked by E.classShouldBeMissing(),
 * doesn't exist in a conflicting package.
 */
public class ClassMissingAllowDestination {

  public static void main(String[] args) {
    E.classShouldBeMissing();
  }
}
