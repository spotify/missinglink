import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StaticMethodBecameInstanceTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void shouldThrowError() throws Exception {
    thrown.expect(IncompatibleClassChangeError.class);
    thrown.expectMessage("Expected static method");
    thrown.expectMessage("staticIsNoise");

    StaticMethodBecameInstance.main(new String[0]);
  }
}
