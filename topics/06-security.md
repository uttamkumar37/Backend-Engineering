# Topic 6 — Security: Spring Security, OAuth2/OIDC, JWT, Keycloak, OWASP

Assumes you already add `@PreAuthorize` and configure a `SecurityFilterChain`. This is about what
those abstractions actually do, why JWTs are a narrower tool than they're often treated as, and
the handful of vulnerability classes that account for most real-world API breaches.

---

## 1. Spring Security internals

- **The filter chain, not the annotations, is the real enforcement point.** `SecurityFilterChain`
  is an ordered list of `jakarta.servlet.Filter`s (`UsernamePasswordAuthenticationFilter`,
  `BearerTokenAuthenticationFilter`, `ExceptionTranslationFilter`, `AuthorizationFilter`, etc.)
  wrapping the actual servlet dispatch. Authentication happens before your controller is ever
  reached; by the time your code runs, `SecurityContextHolder.getContext().getAuthentication()`
  either holds a populated `Authentication` or the request was already rejected upstream.
  Misordering custom filters relative to the built-in ones (e.g., a custom filter reading a
  principal that hasn't been set yet because it runs before `BearerTokenAuthenticationFilter`) is
  a common source of "security works in some code paths but not others" bugs.
- **Method security (`@PreAuthorize`, `@PostAuthorize`) is AOP, not a separate mechanism** — it's
  implemented as a `MethodInterceptor` advice, wrapped around the target bean by the same
  proxy-creation `BeanPostProcessor` covered in Topic 2. This means **the exact same
  self-invocation bug applies**: calling a `@PreAuthorize`-annotated method on `this` from within
  the same class bypasses the check entirely, because the call never goes through the proxy. This
  is a genuinely dangerous variant of the AOP self-invocation trap because the failure mode is
  silent authorization bypass, not a missing feature — it's worth specifically auditing for in
  any codebase using method-level security.
- **`SecurityContextHolder`'s default strategy is `ThreadLocal`-based**, which has direct
  consequences for async/reactive code: spawning a new thread (a `@Async` method, an
  `ExecutorService` submission, or — relevantly to Topic 1 — a virtual-thread-per-task executor)
  does **not** automatically propagate the security context unless you explicitly use
  `DelegatingSecurityContextExecutor`/`DelegatingSecurityContextRunnable`, or set the strategy to
  `MODE_INHERITABLETHREADLOCAL` (which propagates to child threads but not to pooled/reused
  threads, since inheritance only happens at thread *creation* time — irrelevant for a thread
  pool reusing existing threads). Getting this wrong means authorization checks silently see "no
  authenticated user" inside async code, either failing closed (safe but confusing) or, worse,
  failing open if a fallback path assumes unauthenticated means something benign.
- **`AuthenticationManager`/`AuthenticationProvider` is the actual credential-checking
  abstraction** — a resource server validating JWTs plugs in a `JwtAuthenticationProvider`
  (backed by a `JwtDecoder`) rather than a password-checking provider; understanding this
  separation is what lets you reason about a service being simultaneously a resource server (for
  incoming requests) and an OAuth2 client (for outbound calls to other resource servers) with two
  independent, non-conflicting configurations.

---

## 2. OAuth2 vs OIDC — the distinction that actually matters

- **OAuth2 is an authorization framework — it answers "can this token do X," not "who is this."**
  A bare OAuth2 access token proves the bearer was granted some scope of access; it says nothing
  guaranteed about end-user identity unless the authorization server's access token happens to
  encode identity claims by convention. **OIDC is an identity layer built on top of OAuth2** that
  standardizes this: it adds the **ID token** (always a JWT, always contains `sub`, always
  represents "who authenticated," and is meant for the *client*, not to be sent to APIs) alongside
  the access token (meant for *resource servers*, and may or may not be a JWT). Conflating "I
  have an OAuth2 access token" with "I know who the user is" is a common and consequential design
  mistake — use the ID token for identity, the access token for authorization to a specific API.
- **Grant type selection is a real security decision, not boilerplate**: **Authorization Code +
  PKCE** is the standard for any client with a user present (SPA, mobile, and now recommended even
  for traditional confidential server-side web clients) — PKCE (a client-generated code
  verifier/challenge pair) closes the authorization-code-interception attack that plain
  authorization code grant was vulnerable to on public clients. **Client credentials grant** is
  for service-to-service calls with no user context (a backend service calling another backend
  service as itself, not on behalf of a user) — the resulting token typically represents the
  calling *service's* identity/scopes, not a user's, and this distinction must be enforced in
  authorization logic on the receiving side (a client-credentials token should never be treated as
  equivalent to a user session). The **implicit grant and resource owner password grant are both
  deprecated in current OAuth2 security best practice** (implicit exposes tokens in the URL
  fragment/browser history with no client authentication; password grant requires the client
  to handle raw user credentials, defeating the purpose of delegated auth) — seeing either in a
  design review today is a flag to push back on.
- **JWT self-contained validation vs. token introspection is a stateless-vs-stateful trade-off**:
  a resource server validating a JWT locally (checking signature + claims) needs no network call
  to the authorization server per request — fast, scalable, but means the resource server is
  trusting a token's claims as of *issuance* time, with no way to know if it's since been revoked.
  **Introspection** (`RFC 7662`, calling the authorization server per request to ask "is this
  token still valid") gives real-time revocation awareness at the cost of a network round-trip
  and coupling every resource server's request latency to the authorization server's availability
  — this exact trade-off is why short-lived JWT access tokens + refresh tokens is the usual
  compromise (Section 3).

---

## 3. JWT — structure, validation, and the vulnerabilities specific to it

- **Structure**: base64url-encoded `header.payload.signature`. **None of this is encrypted by
  default** — anyone can base64-decode and read the payload; a JWT protects *integrity*
  (tamper-evidence via the signature), not *confidentiality*. Putting sensitive data (PII beyond
  what's needed for authorization, internal system details) in JWT claims because "it's encoded"
  is a real and common mistake — encoding is not encryption.
- **Signature algorithm choice has a real, exploitable difference**: `HS256` is symmetric — the
  same secret both signs and verifies, meaning every resource server that verifies tokens must
  hold the same secret the authorization server uses to sign, which doesn't scale trust boundaries
  (any resource server with the secret could also forge tokens). `RS256`/`ES256` are asymmetric —
  the authorization server signs with a private key, and resource servers verify with the
  corresponding public key (typically fetched from a JWKS endpoint, Section 4) — no resource
  server ever holds signing capability, which is the correct model for a multi-service
  architecture with an external identity provider.
- **The classic `alg: none` and algorithm-confusion attacks** exploit JWT libraries that trust the
  `alg` header the token itself declares: an attacker crafts a token with `alg: none` and no
  signature, or — the more subtle **RS256/HS256 key-confusion attack** — takes a service's known
  *public* RS256 key and resubmits a forged token signed with `HS256` using that public key as
  the HMAC secret; a vulnerable verifier that blindly trusts the token's declared algorithm
  instead of enforcing an expected algorithm will verify it successfully. **The fix is always on
  the verifier side**: explicitly configure and enforce the expected algorithm(s) rather than
  trusting the token's own header — `spring-security-oauth2-resource-server`'s `JwtDecoder` does
  this correctly when configured with a fixed algorithm/JWK source, but a hand-rolled JWT
  verification path is a common place this gets reintroduced.
- **Claims validation beyond the signature is mandatory and often skipped**: `exp` (expiry — also
  check `nbf`, not-before, if used), `iss` (issuer — reject tokens from an authorization server
  you didn't expect, relevant when multiple realms/tenants exist), `aud` (audience — a token
  issued for a *different* resource server/API should be rejected even if the signature is valid
  and unexpired; skipping audience validation is what allows a token meant for service A to be
  replayed against service B if both trust the same issuer). Spring's `JwtDecoder` validates
  `exp`/`nbf` by default but **audience validation must be added explicitly**
  (`JwtClaimValidator` for `aud`) — a very common gap in resource server configs.
- **Revocation is the fundamental limitation of stateless JWTs** — once issued, a JWT is valid
  until it expires; there is no built-in way to invalidate it early (logout, compromised token,
  permission change) without breaking statelessness. The standard mitigation is **short-lived
  access tokens (minutes) + longer-lived refresh tokens** (which *are* checked against the
  authorization server on each use and can be revoked server-side), accepting a bounded exposure
  window rather than achieving instant revocation. A token blacklist/denylist checked on every
  request is sometimes proposed as a fix but reintroduces the exact stateful-lookup-per-request
  cost that JWTs were chosen to avoid — if instant revocation is a hard requirement, that's a
  signal to use introspection (or opaque tokens) for that specific flow rather than forcing a
  stateless mechanism to do a stateful job.

---

## 4. Keycloak in the architecture

- **Keycloak plays the role of both OAuth2 authorization server and OIDC identity provider** — it
  issues access tokens (JWTs by default, RS256-signed) and ID tokens, manages users/credentials,
  and exposes the standard discovery (`/.well-known/openid-configuration`) and **JWKS endpoint**
  (`/protocol/openid-connect/certs`) that resource servers use to fetch the current public
  key(s) for verification.
- **Realms, clients, and roles are the core model**: a realm is an isolated tenant/security
  domain (separate users, clients, and token issuers per realm — commonly one per environment or
  per actual business tenant); a client is a registered application/service allowed to request
  tokens (confidential clients hold a secret for server-side flows, public clients — SPAs, mobile
  — use PKCE instead); roles can be realm-level (global) or client-level (scoped to one
  application) and are what map into Spring Security authorities after token validation.
- **Key rotation is handled transparently through the JWKS endpoint** — Spring's
  `NimbusJwtDecoder` (used under `spring-boot-starter-oauth2-resource-server`) caches the JWKS
  response and matches a token's `kid` (key ID) header to the right public key, refetching when
  an unrecognized `kid` appears. This is why resource servers should point at the JWKS URL rather
  than pinning a single static public key — Keycloak (and any OIDC provider) rotates signing keys
  periodically, and a hardcoded key silently breaks verification the moment rotation happens.
- **Token exchange** (RFC 8693, supported by Keycloak) lets a service trade one token for another
  — e.g., a gateway holding a user's token exchanging it for a service-scoped token to call a
  downstream service on the user's behalf without forwarding the original token verbatim — useful
  for constraining blast radius (a downstream service compromise doesn't expose the original
  broadly-scoped user token) but adds real operational complexity and an extra network hop per use.

---

## 5. OWASP Top 10 — the ones a backend engineer actually causes or prevents

- **Broken Access Control (currently #1) is dominated in practice by IDOR (Insecure Direct Object
  Reference)** — an endpoint like `GET /api/orders/{id}` that checks *authentication* (is there a
  valid token) but not *authorization* (does *this* token's subject actually own order `{id}`)
  lets any authenticated user read/modify any other user's data just by changing the ID in the
  URL. This is the single most common real-world API vulnerability found in penetration tests and
  is a code-review discipline problem, not a framework gap — every resource-fetching endpoint
  needs an explicit ownership/permission check, not just an `@PreAuthorize("isAuthenticated()")`.
- **Injection** (SQL, and increasingly NoSQL/LDAP/command injection) is largely prevented by
  default when using JPA/parameterized `@Query`/`PreparedStatement` consistently — the actual risk
  surface in modern Spring apps is **native queries built via string concatenation** (`@Query
  (nativeQuery = true, value = "... " + userInput)`) or dynamically built `Specification`/criteria
  queries where a raw value is spliced into a `like` pattern without proper parameter binding.
- **Cryptographic failures**: storing passwords with a fast general-purpose hash (or worse,
  reversible encryption) instead of a slow, salted, purpose-built algorithm (`BCrypt`, `Argon2` —
  Spring Security's `PasswordEncoder` defaults to `BCrypt` for exactly this reason); transmitting
  tokens/credentials over plain HTTP internally "because it's inside the VPC" (defense in depth
  matters — an internal network breach shouldn't mean every service-to-service call is trivially
  sniffable); and the JWT algorithm-confusion issue from Section 3 both fall in this category.
- **Security misconfiguration**: default Spring Security CORS/CSRF settings changed meaningfully
  between major versions and are frequently misunderstood — disabling CSRF protection globally
  "to make the API work" for a stateless JWT-based API is usually *correct* (CSRF matters for
  cookie-based session auth, not for APIs where the token must be explicitly attached by the
  client and isn't automatically sent by the browser like a cookie), but doing the same for a
  server that also has cookie-based session endpoints reopens a real vulnerability — this is a
  "know why the default exists" situation, not a "just disable it" situation.
- **Vulnerable/outdated components**: transitive dependency vulnerabilities (a CVE in a Jackson
  or logging library pulled in transitively by a framework, not a direct dependency you'd think to
  check) account for a large share of real incidents (Log4Shell being the canonical example) —
  this is why dependency scanning (OWASP Dependency-Check, Snyk, GitHub Dependabot) needs to be a
  CI gate, not a manual occasional check, and why pinning to "whatever version worked when the
  project started" is itself a security posture, not a neutral choice.
- **SSRF (Server-Side Request Forgery) is specifically elevated in a microservices/cloud
  architecture**: any endpoint that takes a URL or makes an outbound call based on user input
  (webhook registration, "import from URL" features, image-fetch-by-URL) risks being tricked into
  calling internal-only services or cloud metadata endpoints (`169.254.169.254` on AWS/GCP/Azure —
  a classic path to credential theft in cloud environments) that are unreachable from the public
  internet but reachable from inside the service's own network. Mitigation requires strict
  allow-listing of destination hosts/schemes and blocking requests to private IP ranges and the
  cloud metadata address specifically, not just "validate it looks like a URL."

---

## Interview-depth Q&A

1. A `@PreAuthorize`-protected method is being called successfully by a user who shouldn't have
   access, but only when invoked from another method in the same service class. What's happening?
2. Explain the difference between an OIDC ID token and an OAuth2 access token precisely enough
   that a teammate would stop sending the ID token to a resource server API.
3. Why does enforcing `aud` (audience) validation matter even when every service trusts the same
   Keycloak realm and issuer?
4. Describe the RS256/HS256 key-confusion attack in enough detail to explain why a verifier must
   pin the expected algorithm rather than trust the token's own `alg` header.
5. Your team needs to revoke a specific user's access instantly (e.g., they were just terminated),
   but you're using short-lived stateless JWTs. What are the actual options, and what does each cost?
6. Walk through exactly how an IDOR vulnerability would let User A read User B's order given a
   correctly-configured authentication layer — where specifically does the check need to live?

## Proof of learning
_Write one paragraph on an access-control gap, a JWT misconfiguration, or a dependency CVE you've
encountered or can imagine in your own systems — what check or config would have caught it before
it reached production?_
