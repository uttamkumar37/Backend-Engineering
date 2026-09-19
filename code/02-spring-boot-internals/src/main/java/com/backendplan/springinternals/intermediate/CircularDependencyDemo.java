package com.backendplan.springinternals.intermediate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

// Field/setter injection can resolve a circular dependency via the three-level cache
// (an early, unfinished reference is handed out). Constructor injection cannot, because
// a constructor needs a *complete* argument - there is no early reference to hand it.
public class CircularDependencyDemo {

    @Component
    static class FieldServiceA {
        @Autowired FieldServiceB b;
    }

    @Component
    static class FieldServiceB {
        @Autowired FieldServiceA a;
    }

    @Component
    static class CtorServiceA {
        final CtorServiceB b;
        CtorServiceA(CtorServiceB b) { this.b = b; }
    }

    @Component
    static class CtorServiceB {
        final CtorServiceA a;
        CtorServiceB(CtorServiceA a) { this.a = a; }
    }

    public static void main(String[] args) {
        System.out.println("--- Field injection: circular dependency resolves ---");
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(FieldServiceA.class, FieldServiceB.class);
            ctx.refresh();

            FieldServiceA a = ctx.getBean(FieldServiceA.class);
            System.out.println("a.b is set: " + (a.b != null));
            System.out.println("a.b.a is set (same instance as a): " + (a.b.a == a));
        }

        System.out.println("--- Constructor injection: circular dependency fails ---");
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(CtorServiceA.class, CtorServiceB.class);
            ctx.refresh();
            ctx.getBean(CtorServiceA.class);
            System.out.println("This line should not be reached.");
        } catch (Exception e) {
            System.out.println("Failed as expected: " + e.getClass().getSimpleName());
            System.out.println("Root cause: " + rootCause(e).getClass().getSimpleName()
                    + " - " + rootCause(e).getMessage());
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
