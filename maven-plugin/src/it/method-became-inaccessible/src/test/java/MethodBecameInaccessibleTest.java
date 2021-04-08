import org.junit.Assert;
import org.junit.Test;

public class MethodBecameInaccessibleTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(IllegalAccessError.class,
        () -> MethodBecameInaccessible.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("accessLevelToChange"));
  }
}
