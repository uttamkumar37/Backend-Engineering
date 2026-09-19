import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

// Preview API in JDK 21 - run with:
// java --enable-preview --source 21 StructuredConcurrencyDemo.java
public class StructuredConcurrencyDemo {

    record OrderInfo(String orderId) {}
    record CustomerInfo(String customerId) {}

    static OrderInfo fetchOrder() throws InterruptedException {
        Thread.sleep(Duration.ofMillis(200));
        return new OrderInfo("ord-001");
    }

    static CustomerInfo fetchCustomerFails() {
        throw new RuntimeException("customer service unavailable");
    }

    public static void main(String[] args) throws InterruptedException {
        // ShutdownOnFailure cancels sibling subtasks the moment one fails,
        // instead of leaking a thread that keeps running after the caller gives up on it.
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Callable<OrderInfo> orderTask = StructuredConcurrencyDemo::fetchOrder;
            Callable<CustomerInfo> customerTask = StructuredConcurrencyDemo::fetchCustomerFails;

            var orderSubtask = scope.fork(orderTask);
            var customerSubtask = scope.fork(customerTask);

            scope.join();

            try {
                scope.throwIfFailed();
            } catch (Exception e) {
                System.out.println("Scope failed as expected: " + e.getCause());
            }

            System.out.println("Order subtask state: " + orderSubtask.state());
            System.out.println("Customer subtask state: " + customerSubtask.state());
        }
    }
}
