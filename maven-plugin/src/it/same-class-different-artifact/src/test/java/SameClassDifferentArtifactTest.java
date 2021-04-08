import org.junit.Assert;
import org.junit.Test;

public class SameClassDifferentArtifactTest {
  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(NoSuchMethodError.class,
        () -> SameClassDifferentArtifact.main(new String[0]));
    Assert.assertTrue(ex.getMessage().contains("A.methodTwo"));
  }
}
