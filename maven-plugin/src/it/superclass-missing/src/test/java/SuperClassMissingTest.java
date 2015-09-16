import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.NoClassDefFoundError;

public class SuperClassMissingTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void shouldThrowError() throws Exception {
    thrown.expect(NoClassDefFoundError.class);
    thrown.expectMessage("WillGoAway");

    SuperClassMissing.main(new String[0]);
  }
}
