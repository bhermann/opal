package interfaces;

interface Super {
    default void foo() {
        System.out.println("Super");
    }
}

interface Sub1 extends Super {
    abstract void foo();
}

interface Sub2 extends Super {}

interface SubSub extends Sub2, Sub1 {
    // foo is abstract here, must be overridden by implementers!
}

public class Interfaces implements SubSub {

    // Required, as foo is abstract otherwise (Sub1.foo is the maximally specific superinterface
    // method)
    public void foo() { System.out.println("Interfaces"); }

    public static void main(String[] args) {
        new Interfaces().foo();
    }
}

interface Sub3 extends Super {}

interface SubSub2 extends Sub2, Sub3 {
    // Super.foo is available here as a default method
}