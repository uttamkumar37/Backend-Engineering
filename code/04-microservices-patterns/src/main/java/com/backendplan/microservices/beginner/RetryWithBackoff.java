package com.backendplan.microservices.beginner;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// Exponential backoff spaces retries out instead of hammering a struggling dependency
// immediately and repeatedly.
public class RetryWithBackoff {

    static final AtomicInteger attempt = new AtomicInteger();
    static final Instant start = Instant.now();

    static String flakyDependency() {
        int current = attempt.incrementAndGet();
        long elapsedMs = Duration.between(start, Instant.now()).toMillis();
        System.out.println("Attempt " + current + " at +" + elapsedMs + "ms");
        if (current < 3) {
            throw new RuntimeException("transient failure");
        }
        return "success on attempt " + current;
    }

    public static void main(String[] args) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(4)
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(Duration.ofMillis(200), 2.0))
                .build();

        Retry retry = Retry.of("demo", config);
        Supplier<String> decorated = Retry.decorateSupplier(retry, RetryWithBackoff::flakyDependency);

        String result = decorated.get();
        System.out.println("Final result: " + result);
    }
}
