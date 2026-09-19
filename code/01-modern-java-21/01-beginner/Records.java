public class Records {

    record Point(int x, int y) {}

    public static void main(String[] args) {
        Point p1 = new Point(1, 2);
        Point p2 = new Point(1, 2);

        System.out.println(p1);
        System.out.println("x = " + p1.x() + ", y = " + p1.y());
        System.out.println("p1.equals(p2) = " + p1.equals(p2));
        System.out.println("p1.hashCode() == p2.hashCode() = " + (p1.hashCode() == p2.hashCode()));
    }
}
