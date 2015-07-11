import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class InstantiateWithMethodMissingTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void shouldThrowError() throws Exception {
    thrown.expect(AbstractMethodError.class);
    thrown.expectMessage("anotherMethod");

    InstantiateWithMethodMissing.main(new String[0]);
  }
}
