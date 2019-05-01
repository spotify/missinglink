package com.spotify.missinglink.system.sad;

import static org.junit.Assert.assertNotEquals;

import com.google.common.collect.ImmutableList;
import com.spotify.missinglink.Conflict;
import com.spotify.missinglink.system.TestHelper;
import org.junit.Test;

public class SadTest {

  @Test
  public void testMissingClass() throws Exception {
    final ImmutableList<Conflict> conflicts = TestHelper.getConflicts(getClass());
    assertNotEquals(ImmutableList.of(), conflicts);
  }
}
