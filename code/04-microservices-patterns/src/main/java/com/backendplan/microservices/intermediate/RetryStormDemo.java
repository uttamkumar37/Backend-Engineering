package com.backendplan.microservices.intermediate;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// Retry + circuit breaker composed WITHOUT excluding CallNotPermittedException from retry
// multiplies load on a struggling dependency right when it can least afford it, and keeps
// burning retry attempts uselessly once the breaker is open. Excluding it fixes both.
public class RetryStormDemo {

    static AtomicInteger realCalls = new AtomicInteger();
    static AtomicInteger attemptsMade = new AtomicInteger();

    static String failingDependency() {
        realCalls.incrementAndGet();
        throw new RuntimeException("downstream is struggling");
    }

    static CircuitBreaker newCircuitBreaker() {
        return CircuitBreaker.of("demo", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
    }

    static void runScenario(String label, Retry retry, CircuitBreaker circuitBreaker) {
        realCalls.set(0);
        attemptsMade.set(0);

        Supplier<String> withBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, RetryStormDemo::failingDependency);
        // counts EVERY retry attempt, whether it reaches the real dependency or is blocked by the breaker
        Supplier<String> instrumented = () -> {
            attemptsMade.incrementAndGet();
            return withBreaker.get();
        };
        Supplier<String> withRetry = Retry.decorateSupplier(retry, instrumented);

        int businessCalls = 6;
        for (int i = 0; i < businessCalls; i++) {
            try {
                withRetry.get();
            } catch (Exception ignored) {
                // every business call fails - that's expected, we're measuring load generated
            }
        }

        System.out.println(label + ":");
        System.out.println("  Business-level calls made: " + businessCalls);
        System.out.println("  Total attempts (including retries): " + attemptsMade.get());
        System.out.println("  Real calls that reached the dependency: " + realCalls.get());
        System.out.println("  Final circuit state: " + circuitBreaker.getState());
        System.out.println();
    }

    public static void main(String[] args) {
        RetryConfig naiveConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build(); // retries on ANY exception, including CallNotPermittedException

        RetryConfig fixedConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .ignoreExceptions(CallNotPermittedException.class) // don't waste retries on an open circuit
                .build();

        runScenario("Naive (retries even when circuit is OPEN)", Retry.of("naive", naiveConfig), newCircuitBreaker());
        runScenario("Fixed (stops retrying once circuit is OPEN)", Retry.of("fixed", fixedConfig), newCircuitBreaker());
    }
}
