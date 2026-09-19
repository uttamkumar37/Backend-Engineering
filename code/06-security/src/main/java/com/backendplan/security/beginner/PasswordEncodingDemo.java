package com.backendplan.security.beginner;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// BCrypt is slow and salted on purpose: slow resists brute force, salted means the same
// password never produces the same hash twice, which defeats precomputed rainbow tables.
public class PasswordEncodingDemo {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String password = "correct-horse-battery-staple";
        String hash1 = encoder.encode(password);
        String hash2 = encoder.encode(password);

        System.out.println("Hash 1: " + hash1);
        System.out.println("Hash 2: " + hash2);
        System.out.println("Same password, different hashes (different salt): " + !hash1.equals(hash2));

        System.out.println();
        System.out.println("Verifying correct password against hash 1: " + encoder.matches(password, hash1));
        System.out.println("Verifying wrong password against hash 1: " + encoder.matches("wrong-password", hash1));
    }
}
