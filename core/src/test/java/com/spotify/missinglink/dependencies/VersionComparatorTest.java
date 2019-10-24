package com.spotify.missinglink.dependencies;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class VersionComparatorTest {
  @Test
  public void testCorrectOrder() {
    assertOrder(Arrays.asList(
            "0.1.0", "1.2.2", "1.2.3-SNAPSHOT", "1.2.3", "1.2.10", "1.3.0", "1.20.1", "1.20.2-SNAPSHOT", "1.20.2"));
    assertOrder(Arrays.asList("0", "1"));
  }

  private void assertOrder(List<String> versions) {
    int len = versions.size();
    for (int i = 0; i < len; i++) {
      for (int j = i + 1; j < len; j++) {
        String small = versions.get(i);
        String big = versions.get(j);
        assertEquals("Expected " + small + " < " + big, -1, VersionComparator.INSTANCE.compare(small, big));
        assertEquals("Expected " + big + " > " + small, 1, VersionComparator.INSTANCE.compare(big, small));
      }
    }
    for (int i = 0; i < len; i++) {
      String value = versions.get(i);
      assertEquals("Expected " + value + " = " + value, 0, VersionComparator.INSTANCE.compare(value, value));
    }
  }
}