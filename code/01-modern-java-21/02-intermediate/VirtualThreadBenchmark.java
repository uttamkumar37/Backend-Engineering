import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class VirtualThreadBenchmark {

    private static final int TASK_COUNT = 10_000;
    private static final Duration SIMULATED_IO = Duration.ofMillis(100);

    static Duration runWith(ExecutorService executor) {
        Instant start = Instant.now();
        try (executor) {
            IntStream.range(0, TASK_COUNT).forEach(i -> executor.submit(() -> {
                try {
                    Thread.sleep(SIMULATED_IO);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        return Duration.between(start, Instant.now());
    }

    public static void main(String[] args) {
        // fixed pool of 200 platform threads - a realistic size for a traditional Tomcat config
        Duration platformTime = runWith(Executors.newFixedThreadPool(200));
        System.out.println("Platform threads (pool=200): " + platformTime.toMillis() + " ms");

        Duration virtualTime = runWith(Executors.newVirtualThreadPerTaskExecutor());
        System.out.println("Virtual threads (one per task): " + virtualTime.toMillis() + " ms");
    }
}
