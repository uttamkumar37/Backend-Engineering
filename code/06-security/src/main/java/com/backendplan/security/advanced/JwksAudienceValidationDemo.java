package com.backendplan.security.advanced;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

// A resource server validating a JWT's signature locally still needs to check `aud` (audience)
// explicitly - Spring's NimbusJwtDecoder does NOT do this by default. Without it, a token issued
// for a completely different API, but signed by the same trusted issuer, is accepted here too.
public class JwksAudienceValidationDemo {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String kid = "demo-key-1";

        RSAKey jwk = new RSAKey.Builder(publicKey).keyID(kid).build();
        String jwksJson = new JWKSet(jwk).toJSONObject().toString();

        HttpServer server = HttpServer.create(new InetSocketAddress(8089), 0);
        server.createContext("/jwks.json", exchange -> {
            byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String tokenForThisApi = Jwts.builder()
                    .header().add("kid", kid).and()
                    .subject("uttam")
                    .audience().add("orders-api").and()
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(privateKey)
                    .compact();

            String tokenForADifferentApi = Jwts.builder()
                    .header().add("kid", kid).and()
                    .subject("uttam")
                    .audience().add("billing-api") // same issuer/key, but meant for a DIFFERENT service
                    .and()
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(privateKey)
                    .compact();

            NimbusJwtDecoder plainDecoder = NimbusJwtDecoder
                    .withJwkSetUri("http://localhost:8089/jwks.json")
                    .build();

            System.out.println("--- Decoder with NO audience validation (Spring's default) ---");
            Jwt decoded1 = plainDecoder.decode(tokenForThisApi);
            System.out.println("Token for 'orders-api' accepted: " + decoded1.getAudience());

            Jwt decoded2 = plainDecoder.decode(tokenForADifferentApi);
            System.out.println("Token for 'billing-api' ALSO accepted here: " + decoded2.getAudience());
            System.out.println("BUG: this resource server (orders-api) just accepted a token meant for billing-api.");

            System.out.println();
            System.out.println("--- Decoder WITH explicit audience validation ---");
            NimbusJwtDecoder strictDecoder = NimbusJwtDecoder
                    .withJwkSetUri("http://localhost:8089/jwks.json")
                    .build();
            OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                    jwt.getAudience().contains("orders-api")
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(
                                    new OAuth2Error("invalid_token", "Required audience 'orders-api' is missing", null));
            strictDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefault(), audienceValidator));

            Jwt decoded3 = strictDecoder.decode(tokenForThisApi);
            System.out.println("Token for 'orders-api' accepted: " + decoded3.getAudience());

            try {
                strictDecoder.decode(tokenForADifferentApi);
                System.out.println("This should not print - wrong-audience token should be rejected.");
            } catch (JwtValidationException e) {
                System.out.println("Correctly REJECTED wrong-audience token: " + e.getMessage());
            }
        } finally {
            server.stop(0);
        }
    }
}
