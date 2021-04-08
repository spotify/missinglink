import org.junit.Assert;
import org.junit.Test;

public class ReturnTypeChangeTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(NoSuchMethodError.class,
        () -> ReturnTypeChange.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains(")Ljava/lang/String;"));
  }
}
