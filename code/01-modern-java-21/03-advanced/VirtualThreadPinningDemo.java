import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

// Run with: java -Djdk.tracePinnedThreads=full VirtualThreadPinningDemo.java
// The synchronized version prints pinning traces; the ReentrantLock version does not.
public class VirtualThreadPinningDemo {

    private static final Object MONITOR = new Object();
    private static final ReentrantLock LOCK = new ReentrantLock();

    static void blockingCallPinned() {
        synchronized (MONITOR) {
            sleep();
        }
    }

    static void blockingCallNotPinned() {
        LOCK.lock();
        try {
            sleep();
        } finally {
            LOCK.unlock();
        }
    }

    static void sleep() {
        try {
            Thread.sleep(Duration.ofMillis(50));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- synchronized block (pins the carrier thread) ---");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, 50).forEach(i -> executor.submit(VirtualThreadPinningDemo::blockingCallPinned));
        }

        System.out.println("--- ReentrantLock (does not pin) ---");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, 50).forEach(i -> executor.submit(VirtualThreadPinningDemo::blockingCallNotPinned));
        }
    }
}
