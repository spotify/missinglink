package com.spotify.missinglink;

import org.junit.Assert;
import org.junit.Test;

public class ClassMissingAllowDestinationTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(NoClassDefFoundError.class, () -> ClassMissingAllowDestination.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("WillGoAway"));
  }
}
