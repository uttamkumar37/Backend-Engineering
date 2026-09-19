// The State pattern: each state is its own type, and transitions are explicit methods rather
// than a tangle of if/else on a status flag. A sealed interface (Topic 1) is a modern,
// exhaustively-checked way to express exactly this GoF pattern.
public class StatePatternTrafficLightDemo {

    sealed interface LightState permits Red, Yellow, Green {
        LightState next();
    }

    record Red() implements LightState {
        public LightState next() { return new Green(); }
    }

    record Green() implements LightState {
        public LightState next() { return new Yellow(); }
    }

    record Yellow() implements LightState {
        public LightState next() { return new Red(); }
    }

    static String describe(LightState state) {
        return switch (state) {
            case Red r -> "STOP";
            case Yellow y -> "PREPARE TO STOP";
            case Green g -> "GO";
        };
    }

    public static void main(String[] args) {
        LightState current = new Red();
        for (int i = 0; i < 6; i++) {
            System.out.println(current.getClass().getSimpleName() + " -> " + describe(current));
            current = current.next();
        }
    }
}
