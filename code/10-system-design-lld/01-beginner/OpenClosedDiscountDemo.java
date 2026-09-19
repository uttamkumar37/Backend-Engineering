import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Open/Closed via the Strategy pattern: adding a new discount type means adding a new class,
// never editing PriceCalculator's existing, already-tested code.
public class OpenClosedDiscountDemo {

    interface DiscountStrategy {
        BigDecimal apply(BigDecimal price);
    }

    record NoDiscount() implements DiscountStrategy {
        public BigDecimal apply(BigDecimal price) { return price; }
    }

    record PercentageOff(BigDecimal percent) implements DiscountStrategy {
        public BigDecimal apply(BigDecimal price) {
            return price.subtract(price.multiply(percent).divide(BigDecimal.valueOf(100)));
        }
    }

    record FlatAmountOff(BigDecimal amount) implements DiscountStrategy {
        public BigDecimal apply(BigDecimal price) {
            BigDecimal result = price.subtract(amount);
            return result.signum() < 0 ? BigDecimal.ZERO : result;
        }
    }

    static class PriceCalculator {
        // this method never needs to change when a new discount type is added -
        // it depends on the DiscountStrategy abstraction, not on concrete types
        BigDecimal finalPrice(BigDecimal basePrice, DiscountStrategy discount) {
            return discount.apply(basePrice);
        }
    }

    public static void main(String[] args) {
        PriceCalculator calculator = new PriceCalculator();
        BigDecimal base = new BigDecimal("100.00");

        List<Map.Entry<String, DiscountStrategy>> strategies = List.of(
                Map.entry("No discount", new NoDiscount()),
                Map.entry("20% off", new PercentageOff(BigDecimal.valueOf(20))),
                Map.entry("$15 off", new FlatAmountOff(BigDecimal.valueOf(15)))
        );

        for (var entry : strategies) {
            System.out.println(entry.getKey() + ": $" + calculator.finalPrice(base, entry.getValue()));
        }
    }
}
