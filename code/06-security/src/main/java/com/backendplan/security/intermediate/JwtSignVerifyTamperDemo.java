package com.backendplan.security.intermediate;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.util.Date;

// The signature is what makes a JWT tamper-evident. Change even one character of the payload
// and verification fails - that's the entire security property a JWT provides.
public class JwtSignVerifyTamperDemo {

    public static void main(String[] args) {
        SecretKey key = Keys.hmacShaKeyFor("a-very-long-secret-key-for-hs256-demo-purposes-only".getBytes());

        String token = Jwts.builder()
                .subject("uttam")
                .claim("role", "user")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        System.out.println("Signed token: " + token);

        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        System.out.println("Verified successfully. Subject: " + claims.getSubject() + ", role: " + claims.get("role"));

        System.out.println();
        System.out.println("--- Tampering with the payload: changing role from 'user' to 'admin' ---");
        String[] parts = token.split("\\.");
        String tamperedPayload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]))
                .replace("\"role\":\"user\"", "\"role\":\"admin\"");
        String reEncodedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayload.getBytes());
        String tamperedToken = parts[0] + "." + reEncodedPayload + "." + parts[2]; // signature NOT recomputed

        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(tamperedToken);
            System.out.println("This should not print - tampering should be detected.");
        } catch (SignatureException e) {
            System.out.println("Correctly REJECTED tampered token: " + e.getMessage());
        }
    }
}
