package com.backendplan.testing.beginner;

import com.backendplan.testing.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> new Money(new BigDecimal("-1"), "USD"));
    }

    @Test
    void acceptsZeroOrPositiveAmount() {
        Money money = new Money(new BigDecimal("49.99"), "USD");
        assertEquals(new BigDecimal("49.99"), money.amount());
        assertEquals("USD", money.currency());
    }

    @Test
    void equalsIsStructural() {
        Money a = new Money(new BigDecimal("10.00"), "USD");
        Money b = new Money(new BigDecimal("10.00"), "USD");
        assertEquals(a, b, "records compare by value, not identity");
    }
}
