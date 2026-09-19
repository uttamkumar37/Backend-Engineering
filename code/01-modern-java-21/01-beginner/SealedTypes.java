public class SealedTypes {

    sealed interface Shape permits Circle, Square {}
    record Circle(double radius) implements Shape {}
    record Square(double side) implements Shape {}

    static double area(Shape shape) {
        return switch (shape) {
            case Circle c -> Math.PI * c.radius() * c.radius();
            case Square s -> s.side() * s.side();
        };
    }

    public static void main(String[] args) {
        System.out.println("Circle area: " + area(new Circle(2.0)));
        System.out.println("Square area: " + area(new Square(3.0)));
    }
}
