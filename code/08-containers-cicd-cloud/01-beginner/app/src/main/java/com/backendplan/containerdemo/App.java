package com.backendplan.containerdemo;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Deliberately dependency-free (no Spring Boot) so the Dockerfile and the container-memory
// behavior it demonstrates aren't obscured by framework startup noise.
public class App {

    static final List<byte[]> HELD_MEMORY = new ArrayList<>();
    static final AtomicBoolean READY = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        System.out.println("JVM max heap: " + maxHeapMb + " MB (this is what -Xmx/-XX:MaxRAMPercentage controls)");

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/health/live", exchange -> respond(exchange, 200, "alive"));

        server.createContext("/health/ready", exchange ->
                respond(exchange, READY.get() ? 200 : 503, READY.get() ? "ready" : "not ready yet"));

        server.createContext("/ready", exchange -> {
            READY.set(true);
            respond(exchange, 200, "marked ready");
        });

        // simulates real allocation pressure - used by the container-memory-limit demo
        server.createContext("/allocate", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            int mb = 10;
            if (query != null && query.startsWith("mb=")) {
                mb = Integer.parseInt(query.substring(3));
            }
            HELD_MEMORY.add(new byte[mb * 1024 * 1024]);
            long totalHeldMb = HELD_MEMORY.stream().mapToLong(a -> a.length).sum() / (1024 * 1024);
            respond(exchange, 200, "allocated " + mb + " MB, total held: " + totalHeldMb + " MB");
        });

        // a fixed amount of real CPU work, split across all available processors - used by the
        // CPU-throttling demo. Wall-clock time for the SAME amount of work reveals throttling
        // that CPU-usage-percentage metrics alone can hide.
        server.createContext("/cpu-work", exchange -> {
            long start = System.nanoTime();
            int cores = Runtime.getRuntime().availableProcessors();
            long iterationsPerCore = 300_000_000L;
            Thread[] threads = new Thread[cores];
            for (int i = 0; i < cores; i++) {
                threads[i] = new Thread(() -> {
                    long x = 0;
                    for (long j = 0; j < iterationsPerCore; j++) {
                        x += j * j % 7919;
                    }
                    if (x == -1) System.out.println(x); // prevent dead-code elimination
                });
                threads[i].start();
            }
            for (Thread t : threads) {
                try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            respond(exchange, 200, "cpu-work done on " + cores + " visible cores in " + tookMs + " ms");
        });

        server.start();
        System.out.println("Listening on :8080");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
