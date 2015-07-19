import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FieldRemovedTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void shouldThrowError() throws Exception {
    thrown.expect(NoSuchFieldError.class);
    thrown.expectMessage("publicFieldOne");

    FieldRemoved.main(new String[0]);
  }
}
