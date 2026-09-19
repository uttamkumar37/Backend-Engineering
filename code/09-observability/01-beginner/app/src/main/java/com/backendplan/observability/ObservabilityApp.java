package com.backendplan.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Semaphore;

@SpringBootApplication
public class ObservabilityApp {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApp.class, args);
    }

    @RestController
    static class DemoController {
        private final MeterRegistry registry;

        DemoController(MeterRegistry registry) {
            this.registry = registry;
        }

        // GOOD by default: Spring's own http.server.requests metric tags by the ROUTE TEMPLATE
        // ("/orders/{id}"), not the raw path - bounded cardinality regardless of how many
        // distinct order ids are ever requested.
        //
        // BAD, manually created here to prove the point: a custom counter tagged with the RAW
        // id. Every distinct order id creates a brand new time series that never gets reused.
        @GetMapping("/orders/{id}")
        String getOrder(@PathVariable String id) {
            registry.counter("orders.fetched.raw_id_tag_BAD", "order.id", id).increment();
            return "order-" + id;
        }

        @GetMapping("/slow")
        String slow(@RequestParam(defaultValue = "100") long delayMs) throws InterruptedException {
            Thread.sleep(delayMs);
            return "done after " + delayMs + "ms";
        }

        // simulates a capacity-constrained downstream (e.g. a small DB connection pool):
        // only 3 requests can be "in service" at once, everyone else queues.
        private final Semaphore capacity = new Semaphore(3);

        @GetMapping("/bottleneck")
        String bottleneck() throws InterruptedException {
            long waitStart = System.nanoTime();
            capacity.acquire();
            long queuedMs = (System.nanoTime() - waitStart) / 1_000_000;
            try {
                Thread.sleep(200); // fixed "service time" once capacity is acquired
            } finally {
                capacity.release();
            }
            return "served after queueing " + queuedMs + "ms";
        }
    }
}
