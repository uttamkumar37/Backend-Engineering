package com.backendplan.testing.intermediate;

import com.backendplan.testing.domain.Money;
import com.backendplan.testing.domain.NotificationService;
import com.backendplan.testing.domain.OrderService;
import com.backendplan.testing.domain.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Mockito's strict stubbing (the DEFAULT with MockitoExtension) fails a test whose stub is
// never actually matched by a real call. THIS TEST IS DELIBERATELY LEFT RED - run it with
// `mvn test -Dtest=StrictStubbingDemoTest` and read the PotentialStubbingProblem Mockito throws
// EAGERLY, right at the mismatched call site (not deferred to teardown as UnnecessaryStubbingException
// would be for an unmatched stub the code never calls at all) - that failure IS the demonstration.
@ExtendWith(MockitoExtension.class)
class StrictStubbingDemoTest {

    @Mock PaymentGateway paymentGateway;
    @Mock NotificationService notificationService;

    @Test
    void unusedStubTriggersUnnecessaryStubbingException() {
        // stubbed for a SPECIFIC amount the code under test never actually charges
        when(paymentGateway.charge(eq(new Money(new BigDecimal("999.00"), "USD"))))
                .thenReturn("pay-999");

        OrderService service = new OrderService(paymentGateway, notificationService);
        // the real call uses a DIFFERENT amount - the stub above is never matched
        service.placeOrder("ord-1", new Money(new BigDecimal("50.00"), "USD"));

        // no assertion needed - Mockito's own strict-stubbing check fails this test on teardown
    }
}
