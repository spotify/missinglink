/**
 * TODO: document!
 */
public class A {

  public Object publicFieldOne;
  protected Object protectedFieldOne;
  private Object privateFieldOne;
  public static Object staticFieldOne;

  public void internalStaticFieldAccess() { staticFieldOne = ""; }
  public void internalFieldAccess() { A a = new A(); a.publicFieldOne = ""; }

  public void methodOne(String s) {
    System.out.println("a2-one: " + s);
  }

  public void methodTwo(Integer s) {
    System.out.println("a2-two: " + s);
  }

  public void accessLevelToChange() {
    System.out.println("accessible!");
  }

  public static String staticIsNoise(String s, int i) {
    return "static";
  }
}
