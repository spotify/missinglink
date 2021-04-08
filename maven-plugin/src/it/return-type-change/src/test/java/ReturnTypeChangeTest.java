import org.junit.Assert;
import org.junit.Test;

public class ReturnTypeChangeTest {

  @Test
  public void shouldThrowError() throws Exception {
    Throwable ex = Assert.assertThrows(NoSuchMethodError.class,
        () -> ReturnTypeChange.main(new String[0]));

    // the message for NoSuchMethodError seems to have changed after Java 8
    String message = System.getProperty("java.vm.specification.version").startsWith("1.8")
                     ? "SpecificReturnType.aMethod(Ljava/lang/String;)Ljava/lang/String;"
                     : "'java.lang.String SpecificReturnType.aMethod(java.lang.String)'";

    Assert.assertEquals("Exception message: " + ex.getMessage(),
        ex.getMessage(), message);
  }
}
