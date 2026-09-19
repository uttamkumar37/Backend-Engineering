# Topic 7 — Testing: JUnit 5, Mockito, Testcontainers, Contract Testing

Assumes you already write `@Test` methods and `@Mock` collaborators. This is about why a green
test suite full of mocks still ships bugs, and the specific practices that catch what mocking
alone structurally cannot.

---

## 1. JUnit 5 internals

- **The extension model replaced JUnit 4's runners entirely.** `@ExtendWith` composes multiple
  independent extensions (`SpringExtension`, `MockitoExtension`, a custom
  `TestInstancePostProcessor`) instead of being locked into a single `@RunWith` runner — this is
  what makes `@SpringBootTest` and Mockito's JUnit 5 integration composable in the same test class,
  which JUnit 4 couldn't do without workaround runners.
- **Test instance lifecycle is a real behavioral choice, not just style.** The default
  `PER_METHOD` lifecycle creates a fresh test class instance per `@Test` method — instance fields
  never leak state between tests, which is what makes forgetting a `@BeforeEach` reset "usually"
  safe. `@TestInstance(PER_CLASS)` reuses a single instance across all test methods in the class
  (needed for `@BeforeAll`/`@AfterAll` to be non-static, which is convenient with DI) — but this
  reintroduces cross-test state leakage risk through instance fields, and is a common cause of
  "tests pass individually but fail when run together" once someone mutates a shared instance
  field without resetting it in `@BeforeEach`.
- **Parallel execution (`junit.jupiter.execution.parallel.enabled=true`) exposes every implicit
  shared-state assumption in a suite** — static mutable fields, shared `@BeforeAll`-initialized
  resources not designed for concurrent access, or reliance on execution order (a hidden
  dependency between "test A creates a row, test B expects to find it") that happened to be safe
  under sequential execution. Enabling parallelism is often less about "making the suite faster"
  and more about a forcing function that surfaces test isolation bugs that were already latent —
  treat failures it surfaces as real bugs in the test suite, not flakiness to suppress.
- **Parameterized tests (`@ParameterizedTest` + `@MethodSource`/`@CsvSource`/`@ValueSource`)**
  exist specifically to stop copy-pasting near-identical test methods for boundary/edge cases —
  a large block of `@Test` methods differing only in one input/expected-output pair is close to
  always better expressed as one parameterized test, both for maintenance and because it makes the
  actual matrix of cases being covered visible at a glance instead of buried in method names.

---

## 2. Mockito — what mocking actually proves, and where it lies to you

- **A mock proves your code calls its collaborators the way you told the mock to expect — nothing
  about whether that's what the real collaborator actually does.** This is the fundamental
  limitation, not a Mockito quirk: a test that mocks a repository's `save()` to return a
  hand-built entity, then asserts the service method returns that entity correctly, has verified
  the service's *plumbing*, not that the repository call would actually succeed against a real
  database with real constraints (unique indexes, foreign keys, column length limits). This is
  exactly why unit tests with mocked I/O boundaries and integration tests with real infrastructure
  (Section 4) are not redundant — they verify different things.
- **Over-mocking is the most common Mockito anti-pattern**: mocking every single collaborator,
  including simple value objects, pure functions, or the class under test's own internal helper
  methods (via a partial mock/spy), produces a test that asserts the implementation calls methods
  in a specific sequence rather than asserting an observable outcome. The result is a test suite
  that breaks on every refactor even when behavior is unchanged — a strong signal the tests are
  coupled to *how* the code works rather than *what* it does. The fix is testing through the
  public API/observable behavior and mocking only true external boundaries (a repository, an HTTP
  client, a message publisher) — not every internal collaborator.
- **`verify()` overuse compounds the same problem.** Asserting the *return value*/observable state
  after an interaction is usually more valuable and more refactor-resistant than asserting *that a
  specific method was called with specific arguments* — `verify` is appropriate when the
  interaction itself is the entire point (e.g., "did we actually call the payment gateway," where
  there's no other observable side effect to assert on), not as a default replacement for a state
  assertion.
- **Strict stubbing (Mockito's default since 2.x with `MockitoExtension`) throws
  `UnnecessaryStubbingException` for stubs that are never used** by the test — this is a
  deliberate feature, not noise to suppress with `lenient()` — an unused stub is either dead test
  code or a sign the test doesn't actually exercise the path it thinks it does.
- **Mocking `final` classes/static methods requires `mockito-inline`** (bundled by default as of
  Mockito 5, previously an opt-in module) because the classic Mockito mocking mechanism generates
  a CGLIB/ByteBuddy subclass proxy — the exact same subclassing constraint from Topic 2's AOP
  discussion applies here: **you cannot subclass-proxy a final class**, so mocking one needs
  Mockito's inline bytecode-instrumentation mode instead of the standard subclass approach. Needing
  to reach for this frequently (mocking `final` utility classes, static factory methods) is often
  itself a design smell — it usually means a hard dependency that should have been injected as an
  interface instead of called statically.
- **Don't mock value objects/records.** A record's behavior is its data — mocking `new Money(...)`
  instead of just constructing a real instance adds indirection with zero benefit, since there's
  no meaningful "interaction" to stub or verify on an immutable value type; construct real
  instances for test data instead (Section 6).

---

## 3. Test pyramid (or "trophy") shape for a microservice

- **The classic pyramid (many unit tests, fewer integration, few E2E) is the right shape for pure
  business logic but understates integration test value at service boundaries** in a typical
  Spring Boot service, where a large share of real bugs live in the *seams* — a JPA query that
  behaves differently against real Postgres than expected, a Kafka consumer's deserialization
  config, an HTTP client's timeout/retry interaction with an actual downstream contract. This is
  the reasoning behind the "testing trophy" shift some teams describe: fewer trivial unit tests
  for pure logic (often not worth much if the logic is simple), a wide layer of integration tests
  against *real* infrastructure for every I/O boundary, and a thin layer of true end-to-end tests
  reserved for the highest-value user journeys only (E2E tests are slow, flaky, and expensive to
  maintain — they don't scale as the primary safety net once you have more than a couple of
  services).
- **The practical rule**: unit-test complex business/domain logic in isolation (the state-machine
  transition logic from Topic 1's `Order` example is a perfect unit-test target — no I/O, pure
  logic, many edge cases); integration-test every persistence/messaging/HTTP boundary against real
  or realistic infrastructure (Section 4); reserve full end-to-end/system tests for a small number
  of critical business flows, and prefer contract tests (Section 5) over full E2E for verifying
  cross-service compatibility.

---

## 4. Testcontainers

- **The problem it solves**: an embedded/in-memory substitute (H2 standing in for Postgres, a
  fake in-memory Kafka) diverges from real production behavior in ways that matter — H2's SQL
  dialect differs from Postgres's (JSON/JSONB column behavior, specific constraint error types,
  window function support, locking semantics), so a test suite green against H2 can still hit a
  production-only failure the moment a Postgres-specific feature or edge case is involved. This
  divergence is exactly why "our tests all pass but it broke in staging" incidents disproportionately
  trace back to a fake substitute standing in for real infrastructure. Testcontainers runs the
  **actual** database/broker/cache image in Docker for the test, closing this gap.
- **Container lifecycle management is the main practical concern.** Starting a fresh container per
  test class (or worse, per test method) adds real seconds of startup overhead per test and adds
  up fast across a large suite. The standard mitigation is the **singleton container pattern**: a
  single static container instance started once per JVM/test run (via a base test class or a
  JUnit 5 extension) and reused across all test classes, relying on transactional rollback or
  explicit cleanup between tests rather than a fresh container per test for isolation.
- **CI considerations**: Testcontainers requires Docker-in-Docker or a Docker socket mount in the
  CI runner — a common setup gap the first time a team adopts it (GitHub Actions supports Docker
  natively on Linux runners; some managed CI environments need explicit configuration or aren't
  compatible at all). Container startup timing under CI resource contention (shared, throttled CI
  runners) is also a real source of flaky failures if wait strategies aren't configured properly —
  Testcontainers' wait strategies (`waitingFor(Wait.forListeningPort())`/health-check-based
  waiting) should be explicit rather than relying on a fixed sleep, which is both slower than
  necessary in the common case and still flaky under contention.
- **Testcontainers isn't just for databases** — the same real-dependency principle applies to
  Kafka (via the `kafka` module, spinning up an actual broker) and Redis, letting integration tests
  verify actual serialization, partition, and consumer-group behavior (Topic 5) rather than a
  mocked producer/consumer that can't catch a real (de)serialization mismatch.

---

## 5. Contract testing (consumer-driven contracts, e.g., Pact)

- **The problem it solves**: as the number of services grows, full end-to-end integration test
  suites spanning multiple real services become slow, flaky (any one service's instability fails
  unrelated tests), and require standing up a large slice of the whole system just to verify two
  services agree on an API shape — this doesn't scale past a handful of services and is a major
  source of "integration hell" in larger microservice organizations.
- **Consumer-driven contract testing inverts the usual test direction**: the *consumer* of an API
  writes tests against a mock of the provider, and those interactions are recorded as a **contract**
  (a Pact file — the expected request shape and response shape). The **provider** then, in its own
  CI pipeline, replays every contract from every consumer against its *real* implementation and
  verifies it satisfies each one — this catches breaking changes at the provider's build time,
  before a deploy, without either side needing the other running during normal test execution. A
  **Pact Broker** (or equivalent) stores contracts and tracks which provider versions satisfy which
  consumer versions, enabling **"can I deploy safely" checks** (a provider can query the broker:
  "has every currently-deployed consumer's contract been verified against this new version?")
  before a release — this is the actual mechanism that replaces a fragile shared E2E environment.
- **Contract testing is not the same thing as schema validation (OpenAPI/JSON Schema).** An
  OpenAPI spec describes the *shape* of a request/response in the abstract; it doesn't verify the
  provider's *actual runtime behavior* matches it (a provider can drift from its own documented
  spec silently), and it says nothing about which specific fields/values a given consumer actually
  depends on. Contract tests are behavior-verified against real running code on both sides (the
  provider verification step actually invokes the real provider) and are scoped to what consumers
  genuinely use — a provider is free to add new optional fields a consumer doesn't reference without
  breaking anything, which a rigid schema-diff check might flag as a "breaking change" even though
  it isn't one for that consumer.
- **This is a organizational as much as technical practice**: it requires providers to run
  consumer-contract verification in their CI (meaning providers need visibility into consumer
  expectations, via a shared broker), which is a bigger adoption lift than adding a testing library
  — the payoff is specifically proportional to the number of service-to-service integration points
  in the system, and isn't worth the setup cost for a small number of tightly-coupled services
  that deploy together anyway.

---

## 6. Practical hygiene: test doubles, data builders, flakiness

- **Test double taxonomy** (useful vocabulary, not pedantry): a **dummy** is passed but never
  used; a **stub** returns canned answers to calls; a **mock** is a stub that also verifies
  interactions occurred; a **fake** is a lightweight working implementation (an in-memory
  repository implementing the real interface) — often a better choice than a mock for a
  collaborator with real, non-trivial behavior you'd otherwise have to stub extensively, since a
  fake stays correct by construction instead of by an ever-growing list of stubbed calls. A
  **spy** wraps a real object, letting real behavior run while also recording/allowing
  verification of calls.
- **Test data builders** (a fluent builder producing valid domain objects with sensible defaults,
  overridable per test — `anOrder().withStatus(Cancelled("reason")).build()`) solve the problem of
  every test constructing a full valid `Order`/`Customer` inline, which makes tests brittle to
  constructor signature changes and buries the one or two fields that actually matter to that
  test's assertion under a wall of irrelevant setup — the builder isolates that noise in one place.
- **Assertion roulette** (a test with many assertions and a failure message that doesn't say which
  one failed or why) and **flaky tests from timing assumptions** (`Thread.sleep()` instead of
  polling/awaiting a real condition, e.g. with Awaitility) are both concrete, fixable problems, not
  inherent to testing — a flaky test suite that gets "just rerun it" as the standard response
  trains a team to stop trusting CI failures at all, which is a much more expensive failure mode
  than the flaky test itself.

---

## Interview-depth Q&A

1. A test suite is 100% green but a production bug involving a unique-constraint violation slipped
   through. What's the most likely gap in the test suite's design, and why wouldn't more mocking
   have caught it?
2. Explain, concretely, why over-mocking makes a test suite *more* expensive to maintain over time
   rather than less, using a refactor scenario as the example.
3. Why is H2 an incomplete substitute for Postgres in tests, and what specific category of bug does
   that gap let through?
4. Walk through how a consumer-driven contract test would catch a breaking API change before a
   provider deploys it, without either service needing the other running in CI.
5. Your CI suite is flaky roughly 5% of the time on a Testcontainers-based test class. What are the
   first two things you'd check before assuming it's "just Docker being slow"?
6. When would you choose a fake over a mock for a repository dependency in a unit test, and what
   do you give up by doing so?

## Proof of learning
_Write one paragraph on a bug that reached production despite a passing test suite in your own
experience — which testing practice here (real infrastructure, contract testing, less mocking)
would most plausibly have caught it?_
