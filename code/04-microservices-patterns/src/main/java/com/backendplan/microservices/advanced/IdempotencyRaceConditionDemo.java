package com.backendplan.microservices.advanced;

import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

// The exact race condition the concept doc warns about: checking "have I seen this key" and
// then doing the side effect are two separate steps - under concurrency, both callers can pass
// the check before either records the key. A DB unique constraint closes the gap because the
// check-and-record happens as a single atomic operation.
public class IdempotencyRaceConditionDemo {

    private static final String URL = "jdbc:postgresql://localhost/backendplan_demo";
    private static final String USER = System.getProperty("user.name");

    static final AtomicInteger naiveChargeCount = new AtomicInteger();
    static final AtomicInteger safeChargeCount = new AtomicInteger();

    static void naiveRedisOnlyCheck(String idempotencyKey, CountDownLatch startGate) {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            startGate.await();
            // check, then act - NOT atomic, this is the bug
            if (jedis.get(idempotencyKey) == null) {
                Thread.sleep(20); // simulate time between check and the actual charge
                naiveChargeCount.incrementAndGet();
                jedis.set(idempotencyKey, "processed");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void dbUniqueConstraintCheck(String idempotencyKey, CountDownLatch startGate) {
        try (Connection conn = DriverManager.getConnection(URL, USER, "");
             Statement stmt = conn.createStatement()) {
            startGate.await();
            // the INSERT itself is the atomic check-and-record: only one caller can win the unique key
            stmt.executeUpdate("INSERT INTO idempotency_keys(key) VALUES ('" + idempotencyKey + "')");
            safeChargeCount.incrementAndGet();
        } catch (SQLException e) {
            if (!"23505".equals(e.getSQLState())) { // unique_violation - every other caller lands here
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        try (Connection setup = DriverManager.getConnection(URL, USER, "");
             Statement stmt = setup.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS idempotency_keys");
            stmt.execute("CREATE TABLE idempotency_keys (key VARCHAR(100) PRIMARY KEY)");
        }
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            jedis.del("naive:key");
        }

        int concurrentRetries = 15;

        System.out.println("--- Naive Redis-only check-then-act under concurrency ---");
        CountDownLatch gate1 = new CountDownLatch(1);
        Thread[] naiveThreads = new Thread[concurrentRetries];
        for (int i = 0; i < concurrentRetries; i++) {
            naiveThreads[i] = new Thread(() -> naiveRedisOnlyCheck("naive:key", gate1));
            naiveThreads[i].start();
        }
        gate1.countDown();
        for (Thread t : naiveThreads) t.join();
        System.out.println("Payment charged " + naiveChargeCount.get() + " time(s) for " + concurrentRetries + " concurrent retries (BUG: should be 1)");

        System.out.println();
        System.out.println("--- DB unique constraint check-and-record under concurrency ---");
        CountDownLatch gate2 = new CountDownLatch(1);
        Thread[] safeThreads = new Thread[concurrentRetries];
        for (int i = 0; i < concurrentRetries; i++) {
            safeThreads[i] = new Thread(() -> dbUniqueConstraintCheck("safe:key", gate2));
            safeThreads[i].start();
        }
        gate2.countDown();
        for (Thread t : safeThreads) t.join();
        System.out.println("Payment charged " + safeChargeCount.get() + " time(s) for " + concurrentRetries + " concurrent retries (correct: exactly 1)");
    }
}
