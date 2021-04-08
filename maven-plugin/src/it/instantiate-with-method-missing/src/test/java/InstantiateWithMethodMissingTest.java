import org.junit.Assert;
import org.junit.Test;

public class InstantiateWithMethodMissingTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(AbstractMethodError.class,
        () -> InstantiateWithMethodMissing.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("anotherMethod"));
  }
}
