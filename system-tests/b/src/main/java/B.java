/**
 * TODO: document!
 */
public class B {
  public static void methodShouldBeMissing() {
    A a = new A();
    a.methodOne("hi from b");
    a.methodTwo(125);
  }

  public static void classShouldBeMissing() {
    System.out.println(new WillGoAway());
  }

  public static void specificReturnTypeBroken(SpecificReturnType instance) {
    String result = instance.aMethod("parameter");

    System.out.println("b got result: " + result);
  }

  public static AnInterface createAnInterface() {
    return new AnInterfaceImplementation();
  }

  public static void doThings(MethodWillBeRemoved methodWillBeRemoved) {
    methodWillBeRemoved.imGoingAway();
  }

  public static void methodBecameInaccessible() {
    new A().accessLevelToChange();
  }

  public static void methodNoLongerStatic() {
    A.staticIsNoise("hi", 14);
  }

  public static void fieldShouldBeMissing() {
    A a = new A();
    a.publicFieldOne = "Foo";
  }
}
