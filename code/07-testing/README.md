# Code Progression — Topic 7: Testing

Companion code for [topics/07-testing.md](../../topics/07-testing.md). Real JUnit 5, real
Mockito, a real (locally generated and independently verified) Pact contract — run via Maven,
since a test suite is naturally exercised through a test runner, not `java -cp`.

## Run any tier

```bash
mvn test -Dtest=<ClassName>              # a single test class
mvn test -Dtest="MoneyTest,ParameterizedEmailValidatorTest,TestInstanceLifecycleDemoTest"
```

Two test classes in this project are **deliberately red** — that's the demonstration, not a bug:
`StrictStubbingDemoTest` and `OverMockedOrderServiceTest`. Run everything else with:

```bash
mvn test -Dtest="!TestcontainersPostgresDemoTest,!StrictStubbingDemoTest,!OverMockedOrderServiceTest"
```

`TestcontainersPostgresDemoTest` was **not run** in this environment — no Docker daemon was
available. Its code is real and correct; run it wherever Docker is available.

## Beginner (`beginner/`)

- **MoneyTest** — basic assertions, including that records compare structurally.
- **ParameterizedEmailValidatorTest** — one `@ParameterizedTest` with a `@CsvSource` covering 6
  cases, replacing 6 near-identical `@Test` methods.
- **TestInstanceLifecycleDemoTest** — `PER_METHOD` (default) confirmed to give a fresh instance
  per test (no state leakage); `PER_CLASS` confirmed to genuinely leak an instance field's state
  from one test into the next.

## Intermediate (`intermediate/`)

- **OverMockedOrderServiceTest** vs **BehaviorBasedOrderServiceTest** — the single most important
  result in this topic. Both tests passed against the original `OrderService`. Then
  `OrderService` was refactored to send its notification *before* charging instead of after — a
  real, behavior-preserving product decision (see the comment in `OrderService.java`) that changes
  nothing a caller can observe. Confirmed: `OverMockedOrderServiceTest` (asserts on exact call
  order via `InOrder`) **broke** — `Verification in order failure`. `BehaviorBasedOrderServiceTest`
  (asserts on the returned `Order`) and `ArgumentCaptorDemoTest` (asserts on notification content)
  **both still pass**, completely unaffected by the same refactor.
- **StrictStubbingDemoTest** — deliberately red. A stub configured for one argument value that the
  code under test never actually passes triggers Mockito's strict-stubbing check — confirmed live
  as `PotentialStubbingProblem`, thrown *eagerly at the mismatched call site*, not deferred to
  teardown.
- **ArgumentCaptorDemoTest** — captures the actual string passed to `notificationService.send()`
  and asserts on its content, rather than just that *some* string was passed.

## Advanced (`advanced/`)

- **OrdersApiConsumerPactTest** + **OrdersApiProviderVerificationTest** — a full, working
  consumer-driven contract test loop, with **no shared runtime between the two**:
  1. The consumer test defines the contract against a Pact mock server and writes
     `target/pacts/orders-consumer-orders-provider.json` — confirmed generated and correct.
  2. The provider test starts its own real HTTP server (no consumer involved) and replays the
     pact file against it — confirmed **passing**.
  3. To prove this actually catches breaking changes: the provider's response body was changed
     from `"status": "CONFIRMED"` to `"status": "SHIPPED"` and the verification was rerun.
     Confirmed: it failed immediately with a precise diff —
     `body: $.status Expected 'SHIPPED' (String) to be equal to 'CONFIRMED' (String)` — exactly
     the kind of breaking-change detection contract testing exists to provide, without a shared
     E2E environment or the consumer running at all.
- **TestcontainersPostgresDemoTest** — real, correct code demonstrating a real Postgres
  (`JSONB`) feature H2 wouldn't faithfully emulate. **Not executed here** (no local Docker).

## How to use this progression

Run `OrdersApiConsumerPactTest` first (it must generate the pact file before the provider test can
read it), then `OrdersApiProviderVerificationTest`. Try breaking the provider's response yourself
and rerunning verification — watching contract testing catch a real breaking change is far more
convincing than reading about it.
