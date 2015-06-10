/**
 */
public class InstantiateWithMethodMissing {

  public static void main(String[] args) throws Exception {
    AnInterface anInterface = B.createAnInterface();

    anInterface.anotherMethod("hey");
  }
}
