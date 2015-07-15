/**
 * This is expected to generate a runtime error because C cannot be resolved as its superclass
 * is gone from A.
 */
public class SuperClassMissing {

  public static void main(String[] args) {
    System.out.println(C.class.getCanonicalName());
  }
}
