/**
 * TODO: document!
 */
public class A {

  public void methodOne(String s) {
    System.out.println("a1-one: " + s);
  }

  public void methodTwo(Object s) {
    System.out.println("a1-two: " + s);
  }

  private void accessLevelToChange() {
    System.out.println("not accessible...!");
  }

  public String staticIsNoise(String s, int i) {
    return "static";
  }
}
