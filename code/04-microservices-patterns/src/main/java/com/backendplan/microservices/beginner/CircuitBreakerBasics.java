package com.backendplan.microservices.beginner;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// A circuit breaker stops calling a failing dependency once failures cross a threshold,
// instead of letting every caller keep hammering it.
public class CircuitBreakerBasics {

    static final AtomicInteger realCallsMade = new AtomicInteger();

    static String alwaysFailingDependency() {
        realCallsMade.incrementAndGet();
        throw new RuntimeException("downstream service is down");
    }

    public static void main(String[] args) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("demo", config);
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(circuitBreaker,
                CircuitBreakerBasics::alwaysFailingDependency);

        for (int i = 1; i <= 8; i++) {
            String result;
            try {
                result = decorated.get();
            } catch (Exception e) {
                result = "FAILED: " + e.getClass().getSimpleName();
            }
            System.out.printf("Call %d -> %s (circuit state: %s)%n", i, result, circuitBreaker.getState());
        }

        System.out.println();
        System.out.println("Real calls actually made to the dependency: " + realCallsMade.get());
        System.out.println("Total calls attempted by client code: 8");
        System.out.println("The gap is the circuit breaker failing fast once OPEN, without touching the dependency.");
    }
}
