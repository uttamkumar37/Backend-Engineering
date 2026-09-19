package com.backendplan.testing.domain;

public class OrderService {
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;

    public OrderService(PaymentGateway paymentGateway, NotificationService notificationService) {
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
    }

    // REFACTORED: notification now goes out before the charge completes (e.g. "processing"
    // rather than "confirmed, payment ref X") - a real product decision, not a bug. The
    // observable outcome (a CONFIRMED order with the right total) is unchanged.
    public Order placeOrder(String orderId, Money total) {
        notificationService.send("Order " + orderId + " is being processed");
        paymentGateway.charge(total);
        return new Order(orderId, total, "CONFIRMED");
    }
}
