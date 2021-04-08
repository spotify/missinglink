import org.junit.Assert;
import org.junit.Test;

public class FieldRemovedTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(NoSuchFieldError.class,
        () -> FieldRemoved.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("publicFieldOne"));
  }
}
