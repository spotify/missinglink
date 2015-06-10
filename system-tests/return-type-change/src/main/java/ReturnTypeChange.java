/**
 * This is expected to generate a runtime error because A.methodTwo(Integer) doesn't
 * exist when B.methodShouldBeMissing() tries to call it. We should be able to detect that.
 */
public class ReturnTypeChange {

  public static void main(String[] args) {
    B.specificReturnTypeBroken(new SpecificReturnType() {
      @Override
      public Object aMethod(String parameter) {
        return parameter.length();
      }

      @Override
      public Object anotherMethod(String parameter) throws Exception {
        throw new UnsupportedOperationException();
      }
    });
  }
}
