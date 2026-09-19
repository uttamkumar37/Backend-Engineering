package com.backendplan.security.beginner;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// A JWT protects INTEGRITY (tamper-evidence), not CONFIDENTIALITY. Anyone can decode the
// header and payload without any key at all - this demo proves it with plain base64, no
// JWT library, no signature verification.
public class JwtStructureDemo {

    public static void main(String[] args) {
        // a real (expired, harmless) JWT shape: header.payload.signature
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiJ1dHRhbSIsInJvbGUiOiJhZG1pbiIsImV4cCI6MTcwMDAwMDAwMH0." +
                "4Q9Z8k2n7f3xYVh1mR6sT8pL0wC5dE2aB9gH3jK7nMc";

        String[] parts = jwt.split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();

        String header = new String(decoder.decode(parts[0]), StandardCharsets.UTF_8);
        String payload = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8);

        System.out.println("Header (readable with zero keys or secrets): " + header);
        System.out.println("Payload (readable with zero keys or secrets): " + payload);
        System.out.println();
        System.out.println("Notice: role=admin is sitting in plain sight. A JWT's payload is");
        System.out.println("NOT a place to put anything you wouldn't want a curious user to read -");
        System.out.println("only the SIGNATURE (parts[2], base64 of raw bytes, not decodable as text)");
        System.out.println("is what a verifier actually checks to detect tampering.");
    }
}
