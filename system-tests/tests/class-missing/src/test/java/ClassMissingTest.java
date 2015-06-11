import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ClassMissingTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void shouldThrowError() throws Exception {
    thrown.expect(NoClassDefFoundError.class);
    thrown.expectMessage("WillGoAway");

    ClassMissing.main(new String[0]);
  }
}
