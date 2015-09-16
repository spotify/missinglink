/**
 * This is expected to generate a runtime error because A.methodTwo(Integer) doesn't
 * exist when B.methodShouldBeMissing() tries to call it. We should be able to detect that.
 */
public class SameClassDifferentArtifact {

  public static void main(String[] args) {
    A a = new A();
    a.methodOne("hi from main");
    a.methodTwo("hi again from main");
    B.methodShouldBeMissing();
  }
}
