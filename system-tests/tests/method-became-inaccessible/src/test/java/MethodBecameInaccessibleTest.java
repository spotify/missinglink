import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MethodBecameInaccessibleTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void shouldThrowError() throws Exception {
    thrown.expect(IllegalAccessError.class);
    thrown.expectMessage("accessLevelToChange");

    MethodBecameInaccessible.main(new String[0]);
  }
}
