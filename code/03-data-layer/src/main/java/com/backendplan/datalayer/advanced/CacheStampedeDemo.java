package com.backendplan.datalayer.advanced;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

// Cache stampede: when a hot key expires, many concurrent requests miss simultaneously and
// all hammer the "database." A short-lived mutex (SET NX) lets exactly one caller rebuild it.
public class CacheStampedeDemo {

    private static final AtomicInteger dbCallCount = new AtomicInteger();

    static String loadFromSlowDatabase() {
        dbCallCount.incrementAndGet();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "expensive-computed-value";
    }

    static void unprotectedGet(String cacheKey) {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            String cached = jedis.get(cacheKey);
            if (cached == null) {
                String value = loadFromSlowDatabase();
                jedis.setex(cacheKey, 60, value);
            }
        }
    }

    static void mutexProtectedGet(String cacheKey, String lockKey) {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                return;
            }
            // only the caller that successfully sets the lock rebuilds the cache
            String acquired = jedis.set(lockKey, "1", SetParams.setParams().nx().ex(5));
            if ("OK".equals(acquired)) {
                String value = loadFromSlowDatabase();
                jedis.setex(cacheKey, 60, value);
                jedis.del(lockKey);
            } else {
                // another caller is already rebuilding - wait briefly and rely on their result
                try {
                    Thread.sleep(350);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    static void runConcurrent(String label, String cacheKey, Runnable task) throws InterruptedException {
        try (Jedis setup = new Jedis("localhost", 6379)) {
            setup.del(cacheKey);
        }
        dbCallCount.set(0);

        int concurrentRequests = 20;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        for (int i = 0; i < concurrentRequests; i++) {
            new Thread(() -> {
                task.run();
                latch.countDown();
            }).start();
        }
        latch.await();
        System.out.println(label + ": " + dbCallCount.get() + " database calls for " + concurrentRequests + " concurrent requests");
    }

    public static void main(String[] args) throws InterruptedException {
        runConcurrent("Unprotected (stampede)", "stampede:key", () -> unprotectedGet("stampede:key"));
        runConcurrent("Mutex-protected", "protected:key", () -> mutexProtectedGet("protected:key", "protected:lock"));
    }
}
