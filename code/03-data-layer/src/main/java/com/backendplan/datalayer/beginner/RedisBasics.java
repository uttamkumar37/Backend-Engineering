package com.backendplan.datalayer.beginner;

import redis.clients.jedis.Jedis;

// Plain Jedis client - the smallest useful Redis operations: SET/GET/EXPIRE.
public class RedisBasics {

    public static void main(String[] args) throws InterruptedException {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            jedis.set("greeting", "hello from redis");
            System.out.println("GET greeting -> " + jedis.get("greeting"));

            jedis.setex("short-lived", 2, "this will expire in 2 seconds");
            System.out.println("GET short-lived (immediately) -> " + jedis.get("short-lived"));

            Thread.sleep(2500);
            System.out.println("GET short-lived (after 2.5s) -> " + jedis.get("short-lived"));

            jedis.hset("user:1", "name", "Uttam");
            jedis.hset("user:1", "role", "Backend Engineer");
            System.out.println("HGETALL user:1 -> " + jedis.hgetAll("user:1"));
        }
    }
}
