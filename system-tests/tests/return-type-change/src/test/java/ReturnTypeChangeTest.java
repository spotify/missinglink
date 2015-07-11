import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ReturnTypeChangeTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void shouldThrowError() throws Exception {
    thrown.expect(NoSuchMethodError.class);
    thrown.expectMessage(")Ljava/lang/String;");

    ReturnTypeChange.main(new String[0]);
  }
}
