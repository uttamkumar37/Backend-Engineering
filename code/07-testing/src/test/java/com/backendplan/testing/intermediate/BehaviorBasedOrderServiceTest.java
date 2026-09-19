package com.backendplan.testing.intermediate;

import com.backendplan.testing.domain.Money;
import com.backendplan.testing.domain.NotificationService;
import com.backendplan.testing.domain.Order;
import com.backendplan.testing.domain.OrderService;
import com.backendplan.testing.domain.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// This test asserts on the OBSERVABLE OUTCOME - the returned Order is confirmed with the
// right total. It doesn't care what order internal calls happen in, so a refactor that
// reorders or restructures OrderService's internals (without changing behavior) won't break it.
@ExtendWith(MockitoExtension.class)
class BehaviorBasedOrderServiceTest {

    @Mock PaymentGateway paymentGateway;
    @Mock NotificationService notificationService;

    @Test
    void placingAnOrderReturnsAConfirmedOrderWithTheRightTotal() {
        when(paymentGateway.charge(any())).thenReturn("pay-123");
        OrderService service = new OrderService(paymentGateway, notificationService);

        Order result = service.placeOrder("ord-1", new Money(new BigDecimal("50.00"), "USD"));

        assertEquals("CONFIRMED", result.status());
        assertEquals(new BigDecimal("50.00"), result.total().amount());
    }
}
