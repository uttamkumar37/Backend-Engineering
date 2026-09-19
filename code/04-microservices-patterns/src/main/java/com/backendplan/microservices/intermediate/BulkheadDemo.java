package com.backendplan.microservices.intermediate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// A bulkhead isolates one dependency's resource usage from another's, so a slow/saturated
// dependency B can't starve calls to an unrelated, healthy dependency A.
public class BulkheadDemo {

    static void slowDependencyB() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void fastDependencyA() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("--- Shared thread pool (no bulkhead): A gets stuck behind B ---");
        try (ExecutorService sharedPool = Executors.newFixedThreadPool(4)) {
            List<Future<?>> bTasks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                bTasks.add(sharedPool.submit(BulkheadDemo::slowDependencyB));
            }

            Thread.sleep(50); // let B's slow tasks occupy the pool first
            Instant start = Instant.now();
            Future<?> aTask = sharedPool.submit(BulkheadDemo::fastDependencyA);
            aTask.get();
            System.out.println("Fast call A completed in " + Duration.between(start, Instant.now()).toMillis()
                    + " ms (queued behind slow B calls sharing the same pool)");

            for (Future<?> t : bTasks) t.get();
        }

        System.out.println();
        System.out.println("--- Separate pools per dependency (bulkhead): A is unaffected by B ---");
        try (ExecutorService poolA = Executors.newFixedThreadPool(2);
             ExecutorService poolB = Executors.newFixedThreadPool(4)) {

            List<Future<?>> bTasks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                bTasks.add(poolB.submit(BulkheadDemo::slowDependencyB));
            }

            Thread.sleep(50);
            Instant start = Instant.now();
            Future<?> aTask = poolA.submit(BulkheadDemo::fastDependencyA);
            aTask.get();
            System.out.println("Fast call A completed in " + Duration.between(start, Instant.now()).toMillis()
                    + " ms (its own pool, untouched by B's saturation)");

            for (Future<?> t : bTasks) t.get();
        }
    }
}
