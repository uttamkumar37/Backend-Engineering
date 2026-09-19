package com.backendplan.datalayer.intermediate;

import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.time.Instant;

// Cache-aside: check the cache first, fall back to the "database" on a miss, populate the
// cache for next time. Real Redis, a deliberately slow fake DB, real timing measurements.
public class CacheAsideDemo {

    static String loadFromSlowDatabase(String productId) {
        try {
            Thread.sleep(200); // simulated DB latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Product-" + productId + "-details-from-db";
    }

    static String getWithCacheAside(Jedis jedis, String productId) {
        String cacheKey = "product:" + productId;
        String cached = jedis.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String fromDb = loadFromSlowDatabase(productId);
        jedis.setex(cacheKey, 60, fromDb);
        return fromDb;
    }

    public static void main(String[] args) {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            jedis.del("product:42");

            Instant t1 = Instant.now();
            String first = getWithCacheAside(jedis, "42");
            Duration firstCallTime = Duration.between(t1, Instant.now());
            System.out.println("First call (cache miss): " + first + " in " + firstCallTime.toMillis() + " ms");

            Instant t2 = Instant.now();
            String second = getWithCacheAside(jedis, "42");
            Duration secondCallTime = Duration.between(t2, Instant.now());
            System.out.println("Second call (cache hit): " + second + " in " + secondCallTime.toMillis() + " ms");

            System.out.println("Speedup: cache hit was " +
                    (firstCallTime.toMillis() / Math.max(1, secondCallTime.toMillis())) + "x faster");
        }
    }
}
