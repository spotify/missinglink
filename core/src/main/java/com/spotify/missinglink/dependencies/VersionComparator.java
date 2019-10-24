package com.spotify.missinglink.dependencies;

import com.g00fy2.versioncompare.Version;

import java.util.Comparator;

public class VersionComparator implements Comparator<String> {

  public static final VersionComparator INSTANCE = new VersionComparator();

  @Override
  public int compare(String s1, String s2) {
    Version v1 = new Version(s1);
    Version v2 = new Version(s2);
    if (v1.isLowerThan(v2)) {
      return -1;
    }
    if (v1.isHigherThan(v2)) {
      return 1;
    }
    return 0;
  }
}
