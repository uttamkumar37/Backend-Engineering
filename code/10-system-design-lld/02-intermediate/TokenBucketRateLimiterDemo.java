import java.time.Duration;
import java.time.Instant;

// Token bucket: tokens refill at a fixed rate, a request consumes a token, and the bucket can
// hold up to its capacity - allowing reasonable BURSTS while still enforcing a long-term average
// rate. This is why it's the default choice over a strictly-flat leaky bucket for most APIs.
public class TokenBucketRateLimiterDemo {

    static class TokenBucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private Instant lastRefill;

        TokenBucket(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
            this.lastRefill = Instant.now();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            Instant now = Instant.now();
            double elapsedSeconds = Duration.between(lastRefill, now).toMillis() / 1000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefill = now;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        TokenBucket bucket = new TokenBucket(5, 2); // burst of 5, sustained 2/sec after that

        System.out.println("--- Burst of 8 immediate requests (capacity is 5) ---");
        for (int i = 1; i <= 8; i++) {
            System.out.println("request " + i + ": " + (bucket.tryConsume() ? "ALLOWED" : "REJECTED"));
        }

        System.out.println();
        System.out.println("--- Waiting 1 second to refill (2 tokens/sec) ---");
        Thread.sleep(1000);
        for (int i = 1; i <= 3; i++) {
            System.out.println("request " + i + " after wait: " + (bucket.tryConsume() ? "ALLOWED" : "REJECTED"));
        }
    }
}
