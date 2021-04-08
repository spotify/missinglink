import org.junit.Assert;
import org.junit.Test;

public class MethodRemovedTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(NoSuchMethodError.class,
        () -> MethodRemoved.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("A.methodTwo"));
  }
}
