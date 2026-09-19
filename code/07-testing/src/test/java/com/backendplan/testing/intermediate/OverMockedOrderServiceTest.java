package com.backendplan.testing.intermediate;

import com.backendplan.testing.domain.Money;
import com.backendplan.testing.domain.NotificationService;
import com.backendplan.testing.domain.OrderService;
import com.backendplan.testing.domain.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

// This test is coupled to IMPLEMENTATION - it verifies the exact call sequence, not just the
// outcome. THIS TEST IS DELIBERATELY LEFT RED: OrderService was refactored to send the
// notification before charging (a real, behavior-preserving product decision - see
// OrderService's comment), and this test now fails on the InOrder verification even though
// nothing a caller can observe actually changed. Compare against BehaviorBasedOrderServiceTest,
// which asserts on the returned Order and is untouched by the same refactor.
@ExtendWith(MockitoExtension.class)
class OverMockedOrderServiceTest {

    @Mock PaymentGateway paymentGateway;
    @Mock NotificationService notificationService;

    @Test
    void chargesBeforeNotifying_inThatExactOrder() {
        when(paymentGateway.charge(any())).thenReturn("pay-123");
        OrderService service = new OrderService(paymentGateway, notificationService);

        service.placeOrder("ord-1", new Money(new BigDecimal("50.00"), "USD"));

        InOrder inOrder = inOrder(paymentGateway, notificationService);
        inOrder.verify(paymentGateway).charge(any());
        inOrder.verify(notificationService).send(anyString());
    }
}
