import java.math.BigDecimal;

public class OrderDomainModel {

    record Money(BigDecimal amount, String currency) {
        Money {
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("amount cannot be negative: " + amount);
            }
        }
    }

    sealed interface OrderStatus permits
            OrderStatus.Created, OrderStatus.Paid, OrderStatus.Shipped, OrderStatus.Cancelled {
        record Created() implements OrderStatus {}
        record Paid(String paymentId) implements OrderStatus {}
        record Shipped(String trackingNumber) implements OrderStatus {}
        record Cancelled(String reason) implements OrderStatus {}
    }

    record Order(String id, Money total, OrderStatus status) {

        static Order create(String id, Money total) {
            return new Order(id, total, new OrderStatus.Created());
        }

        // the state machine lives here - no other code path can build an invalid transition
        Order markPaid(String paymentId) {
            if (!(status instanceof OrderStatus.Created)) {
                throw new IllegalStateException("Cannot pay an order in status: " + status);
            }
            return new Order(id, total, new OrderStatus.Paid(paymentId));
        }

        Order markShipped(String trackingNumber) {
            if (!(status instanceof OrderStatus.Paid)) {
                throw new IllegalStateException("Cannot ship an order in status: " + status);
            }
            return new Order(id, total, new OrderStatus.Shipped(trackingNumber));
        }

        Order cancel(String reason) {
            if (status instanceof OrderStatus.Shipped) {
                throw new IllegalStateException("Cannot cancel a shipped order");
            }
            return new Order(id, total, new OrderStatus.Cancelled(reason));
        }
    }

    public static void main(String[] args) {
        Order order = Order.create("ord-001", new Money(new BigDecimal("499.00"), "USD"));
        System.out.println(order);

        order = order.markPaid("pay-123");
        System.out.println(order);

        order = order.markShipped("trk-456");
        System.out.println(order);

        try {
            order.cancel("changed mind");
        } catch (IllegalStateException e) {
            System.out.println("Rejected invalid transition as expected: " + e.getMessage());
        }
    }
}
