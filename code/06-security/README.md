# Code Progression — Topic 6: Security

Companion code for [topics/06-security.md](../../topics/06-security.md). Real Spring Security,
real jjwt-signed tokens, a real embedded JWKS HTTP endpoint — no Keycloak server was stood up for
this (that's a heavier standalone install); the JWKS/OIDC resource-server mechanics are
demonstrated against a local mock JWKS endpoint, which is functionally the same shape Keycloak
would serve at `/protocol/openid-connect/certs`.

## Build once, then run any demo

```bash
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:$(cat cp.txt)"
java -cp "$CP" <fully.qualified.DemoClassName>
```

## Beginner (`beginner/`)

- **PasswordEncodingDemo** — BCrypt: the same password produces a different hash every time
  (confirmed), and only the correct password matches on verify.
- **JwtStructureDemo** — decodes a JWT's header and payload with nothing but `Base64.getUrlDecoder()`
  — no library, no key. Confirms a JWT is tamper-evident, not confidential.
- **SpringSecurityFilterChainDemo** — a real embedded Tomcat with a public and a protected
  endpoint. Confirmed: the protected endpoint returns 401 with no credentials, **before the
  controller method ever runs**, and 200 with Basic Auth credentials.

## Intermediate (`intermediate/`)

- **MethodSecuritySelfInvocationDemo** — the security-critical variant of the AOP self-invocation
  bug from Topic 2. Confirmed live: a non-admin user calling the protected method **directly**
  gets `AccessDeniedException`; the same user calling the exact same method via
  `this.deleteAllReports()` from within the class **succeeds** — the proxy was bypassed and the
  authorization check silently never ran.
- **JwtSignVerifyTamperDemo** — signs a token, then tampers with one field in the payload without
  recomputing the signature. Confirmed: verification throws a real `SignatureException`.
- **IdorVulnerabilityDemo** — a real embedded server with a vulnerable endpoint (checks
  authentication, not ownership) and a fixed one. Confirmed: `user-B` successfully fetched
  `user-A`'s order on the vulnerable endpoint; the fixed endpoint returned `403 FORBIDDEN` for the
  identical request.

## Advanced (`advanced/`)

- **AlgorithmConfusionAttackDemo** — generates a real RSA keypair, signs a legitimate RS256 token
  with the private key, then **forges an HS256 "admin" token using the public key's raw bytes as
  the HMAC secret**. Confirmed: a verifier that just matches the token's declared algorithm to a
  key type **accepts the forged admin token**; a verifier pinned to `verifyWith(RSAPublicKey)`
  rejects it outright with `UnsupportedJwtException`. This is a real, working instance of the
  classic RS256/HS256 key-confusion exploit, not a description of one.
- **JwksAudienceValidationDemo** — a real embedded HTTP server serving a JWKS document, and
  Spring Security's actual `NimbusJwtDecoder` fetching from it. Confirmed: Spring's **default**
  decoder accepts a token whose `aud` claim is for a completely different API (`billing-api`) at
  an `orders-api` resource server, because audience validation isn't included by default; adding
  an explicit `OAuth2TokenValidator` for `aud` correctly rejects it.

## How to use this progression

Run each demo and read the actual exception types and accept/reject outcomes before re-reading
the matching section of the concept doc. The self-invocation bypass and the forged-admin-token
acceptance are the two results worth taking most seriously — both are realistic, not contrived,
production vulnerabilities.
