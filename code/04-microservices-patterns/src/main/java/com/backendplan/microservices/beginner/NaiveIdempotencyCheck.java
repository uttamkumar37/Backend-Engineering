package com.backendplan.microservices.beginner;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// The simplest possible idempotency guard: remember keys we've already processed.
// Works fine single-threaded - the advanced demo shows exactly where this breaks under concurrency.
public class NaiveIdempotencyCheck {

    static final Set<String> processedKeys = ConcurrentHashMap.newKeySet();
    static int chargesMade = 0;

    static void chargePayment(String idempotencyKey) {
        if (processedKeys.contains(idempotencyKey)) {
            System.out.println("Skipped duplicate request for key: " + idempotencyKey);
            return;
        }
        processedKeys.add(idempotencyKey);
        chargesMade++;
        System.out.println("Charged payment for key: " + idempotencyKey);
    }

    public static void main(String[] args) {
        chargePayment("order-123-attempt-1");
        chargePayment("order-123-attempt-1"); // client retried with the same key
        chargePayment("order-456-attempt-1");

        System.out.println("Total charges made: " + chargesMade + " (expected: 2)");
    }
}
