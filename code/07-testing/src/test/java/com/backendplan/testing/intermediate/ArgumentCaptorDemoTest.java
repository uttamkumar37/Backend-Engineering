package com.backendplan.testing.intermediate;

import com.backendplan.testing.domain.Money;
import com.backendplan.testing.domain.NotificationService;
import com.backendplan.testing.domain.OrderService;
import com.backendplan.testing.domain.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// An ArgumentCaptor is for when the interaction's CONTENT is the point - here, verifying the
// notification message actually mentions the order it's about, not just that send() was called
// with "some string." (Message content reflects the post-refactor "processing" notification -
// see OrderService and the over-mocked-vs-behavior-based tests in this package for why.)
@ExtendWith(MockitoExtension.class)
class ArgumentCaptorDemoTest {

    @Mock PaymentGateway paymentGateway;
    @Mock NotificationService notificationService;
    @Captor ArgumentCaptor<String> messageCaptor;

    @Test
    void notificationMessageMentionsTheOrderId() {
        when(paymentGateway.charge(any())).thenReturn("pay-abc-123");
        OrderService service = new OrderService(paymentGateway, notificationService);

        service.placeOrder("ord-1", new Money(new BigDecimal("50.00"), "USD"));

        verify(notificationService).send(messageCaptor.capture());
        String actualMessage = messageCaptor.getValue();
        assertTrue(actualMessage.contains("ord-1"),
                "notification should reference the order it's actually about");
    }
}
