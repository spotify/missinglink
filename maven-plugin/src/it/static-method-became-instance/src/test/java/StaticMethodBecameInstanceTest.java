import org.junit.Assert;
import org.junit.Test;

public class StaticMethodBecameInstanceTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(IncompatibleClassChangeError.class,
        () -> StaticMethodBecameInstance.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("Expected static method"));
    Assert.assertTrue(ex.getMessage().contains("staticIsNoise"));
  }
}
