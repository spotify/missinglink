package com.spotify.missinglink.system.happy;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.spotify.missinglink.Conflict;
import com.spotify.missinglink.system.TestHelper;
import org.junit.Test;

public class HappyTest {

  @Test
  public void testHappy() throws Exception {
    final ImmutableList<Conflict> conflicts = TestHelper.getConflicts(getClass());
    assertEquals(ImmutableList.of(), conflicts);
  }
}
