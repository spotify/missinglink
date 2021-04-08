import org.junit.Assert;
import org.junit.Test;

public class ClassMissingTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(NoClassDefFoundError.class, () -> ClassMissing.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("WillGoAway"));
  }
}
