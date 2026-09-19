package com.backendplan.testing.domain;

public interface PaymentGateway {
    String charge(Money amount);
}
