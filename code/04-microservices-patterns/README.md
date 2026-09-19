# Code Progression — Topic 4: Microservices Patterns

Companion code for [topics/04-microservices-patterns.md](../../topics/04-microservices-patterns.md).
Real Resilience4j, real Postgres, real Redis, real concurrent threads — every number below came
from actually running the demo.

## Prerequisites

Same as Topic 3: a running Postgres (`createdb backendplan_demo`) and Redis
(`brew services run postgresql@16` / `brew services run redis`).

## Build once, then run any demo

```bash
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:$(cat cp.txt)"
java -cp "$CP" <fully.qualified.DemoClassName>
```

## Beginner (`beginner/`)

- **CircuitBreakerBasics** — a Resilience4j circuit breaker against an always-failing dependency.
  Confirmed: the breaker trips to `OPEN` after 5 calls (50% failure threshold, window of 5), and
  calls 6–8 throw `CallNotPermittedException` **without touching the real dependency** — 5 real
  calls made for 8 attempted.
- **RetryWithBackoff** — exponential backoff (200ms base, factor 2) against a dependency that
  fails twice then succeeds. Confirmed real attempt timings: +20ms, +231ms, +636ms — each gap
  roughly double the last.
- **NaiveIdempotencyCheck** — the simplest in-memory idempotency guard, correct single-threaded.
  Sets up the exact gap the advanced demo exploits under concurrency.

## Intermediate (`intermediate/`)

- **RetryStormDemo** — the same circuit breaker + retry combination, configured two ways.
  Confirmed: retrying **without** excluding `CallNotPermittedException` burned **18 total
  attempts** for 6 business calls; excluding it dropped that to **9** — both capped real
  dependency load at 4 calls, but the naive config wasted double the attempts once the breaker
  was open, exactly the retry-storm waste the concept doc describes.
- **BulkheadDemo** — a fast dependency A sharing a thread pool with a slow, saturated dependency
  B. Confirmed: A took **477ms** queued behind B on a shared pool, vs **26ms** on its own isolated
  pool — an 18x difference from resource isolation alone.
- **OutboxPatternDemo** — a real Postgres transaction inserting a business row and an outbox row
  together, then a separate relay step publishing pending events and marking them sent (idempotent
  on rerun — a second relay call published 0 new events).

## Advanced (`advanced/`)

- **IdempotencyRaceConditionDemo** — 15 concurrent "retry" threads racing on the same idempotency
  key. Confirmed: the naive Redis-only check-then-act path **charged the payment 15 times**; the
  same scenario against a Postgres unique constraint (`INSERT ... ON CONFLICT` via a caught
  `23505` unique-violation) charged it **exactly once**. This is the single most important result
  in this topic's code — the concept doc's warning made concrete with a real race.
- **SagaOrchestrationDemo** — a sealed `SagaResult` (`Success`/`Compensated`) driving an
  orchestrated saga; when `ScheduleShipment` fails, `ChargePayment` and `ReserveInventory`
  compensate in reverse order, and the exhaustive `switch` over the sealed result forces both
  outcomes to be handled explicitly — the same discipline from Topic 1 applied to a saga's outcome.
- **CqrsProjectionDemo** — a normalized write model and a denormalized read model connected by an
  intentionally-delayed async projector. Confirmed: querying the read model immediately after the
  write returned `null` (projection hadn't run yet); querying again after the projector caught up
  returned the correct summary — CQRS's eventual-consistency window made visible, not hidden.

## How to use this progression

Run each demo and look at the actual counts before re-reading the matching section of the concept
doc. The idempotency race (15 duplicate charges vs 1) and the bulkhead timing gap (477ms vs 26ms)
are far more convincing seen live than described in prose.
