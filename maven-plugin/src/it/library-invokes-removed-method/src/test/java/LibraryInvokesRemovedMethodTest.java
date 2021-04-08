import org.junit.Assert;
import org.junit.Test;

public class LibraryInvokesRemovedMethodTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(NoSuchMethodError.class,
        () -> LibraryInvokesRemovedMethod.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("imGoingAway"));
  }
}
