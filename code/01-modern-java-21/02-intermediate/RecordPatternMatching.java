import java.math.BigDecimal;

public class RecordPatternMatching {

    sealed interface OrderStatus permits Created, Paid, Shipped, Cancelled {}
    record Created() implements OrderStatus {}
    record Paid(BigDecimal amount) implements OrderStatus {}
    record Shipped(String trackingNumber) implements OrderStatus {}
    record Cancelled(String reason) implements OrderStatus {}

    static String describe(OrderStatus status) {
        return switch (status) {
            case Created c -> "Awaiting payment.";
            case Paid(BigDecimal amount) when amount.compareTo(new BigDecimal("1000")) > 0 ->
                    "High-value payment received: " + amount;
            case Paid(BigDecimal amount) -> "Payment received: " + amount;
            case Shipped(String tracking) -> "Shipped, tracking: " + tracking;
            case Cancelled(String reason) -> "Cancelled: " + reason;
        };
    }

    public static void main(String[] args) {
        OrderStatus[] statuses = {
                new Created(),
                new Paid(new BigDecimal("49.99")),
                new Paid(new BigDecimal("2500.00")),
                new Shipped("trk-789"),
                new Cancelled("out of stock")
        };

        for (OrderStatus status : statuses) {
            System.out.println(describe(status));
        }
    }
}
