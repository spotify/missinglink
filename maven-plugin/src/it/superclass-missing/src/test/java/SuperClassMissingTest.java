import org.junit.Assert;
import org.junit.Test;

import java.lang.NoClassDefFoundError;

public class SuperClassMissingTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(NoClassDefFoundError.class,
        () -> SuperClassMissing.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("WillGoAway"));
  }
}
