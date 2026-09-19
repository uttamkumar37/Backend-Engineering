# Topic 4 — Microservices Patterns: Gateway, Resilience, Saga, Outbox, CQRS, Idempotency

Assumes you already know "microservices talk over HTTP/messaging instead of in-process calls."
This is about the failure modes that only show up once you have more than one service and a
network between them, and the patterns that exist specifically to survive those failures.

---

## 1. API Gateway

- **The gateway exists to centralize cross-cutting concerns**, not to be a smart router with
  business logic: authentication/token validation, rate limiting, TLS termination, request
  logging/correlation IDs, and coarse-grained routing. The moment a gateway starts making business
  decisions (e.g., "if order total > $500, route to fraud-check service first"), it has become a
  second, undeclared service with none of the deployment/testing discipline of a real one — a
  common source of "nobody knows why this request behaves differently" bugs.
- **BFF (Backend-for-Frontend)** is a different problem from a general API gateway: it's a
  per-client-type aggregation layer (mobile BFF, web BFF) that composes calls to multiple
  downstream services into a shape that specific client needs. Conflating "the" gateway with a BFF
  causes the gateway to accumulate client-specific response-shaping logic that should live closer
  to the client team owning that need.
- **Gateway aggregation vs. client-side composition**: aggregating multiple backend calls behind
  one gateway endpoint reduces client round-trips and chattiness (good for mobile/high-latency
  clients) but couples the gateway to the availability of every aggregated service — if the
  gateway waits synchronously on 4 downstream calls, its own latency/error rate becomes the max of
  all four, and a single slow dependency degrades every client, including ones that didn't need
  that data. Partial-failure handling (return what succeeded, mark the rest degraded) is
  mandatory, not optional, in an aggregating gateway.
- **The gateway is a single point of failure and a potential bottleneck by construction** — every
  request flows through it. This is why gateways are typically stateless and horizontally scaled
  behind a load balancer, and why heavy computation or long-lived connections don't belong there.
  A service mesh (sidecar proxies like Envoy/Istio) solves a *different* problem — service-to-
  service traffic policy (mTLS, retries, circuit breaking between internal services) — and is
  complementary to, not a replacement for, an edge API gateway; conflating the two leads to
  duplicated or conflicting resilience policy at two layers.

---

## 2. Circuit Breaker, Retry, Bulkhead, Timeout — used together, not alone

- **A circuit breaker (Resilience4j model) has three states**: `CLOSED` (calls pass through,
  failures counted), `OPEN` (calls fail immediately without attempting the network call, after
  the failure rate crosses a threshold within a sliding window), `HALF_OPEN` (after a wait
  duration, a limited number of trial calls are allowed through to test recovery — if they
  succeed, back to `CLOSED`; if they fail, back to `OPEN`). **The point of `OPEN` is to stop
  hammering a failing/slow dependency** — without it, every caller keeps retrying against a
  struggling service, consuming its remaining capacity and delaying its recovery (this is exactly
  how a single slow dependency cascades into a full outage).
- **Count-based vs time-based sliding windows** change what "failure rate" means under bursty
  traffic: a count-based window (last N calls) can trip open from a burst of 10 calls in 200ms
  even if the service is fine on average, while a time-based window (last 60 seconds) is less
  sensitive to burst shape but slower to react to a real sudden outage. Pick based on the traffic
  shape of the actual caller, not a default left unexamined.
- **Retry + circuit breaker interaction is where most misconfigurations happen.** A naive retry
  policy (3 attempts, no backoff) on top of a circuit breaker multiplies load on an already
  struggling dependency by up to 3x right when it can least afford it — this is a **retry storm**,
  a major cause of cascading failures in real incidents. The fix is **exponential backoff with
  jitter** (randomized delay, not just exponential, to avoid synchronized retry waves from many
  clients failing at the same moment) and treating "circuit is open" as **not retryable at all** —
  fail fast and let the breaker do its job instead of retrying into an open circuit.
- **Bulkhead isolates one dependency's failure from exhausting resources needed by others** —
  named after ship compartments. A thread-pool bulkhead (separate, bounded thread pool per
  downstream dependency) means a slow payment-service call can't consume all of the request-
  handling threads that unrelated calls to inventory-service also need — without it, one slow
  dependency degrades unrelated functionality through shared resource exhaustion (a classic
  Netflix-Hystrix-era lesson, still fully applicable with Resilience4j or virtual threads).
- **Timeout is the most-often-missing piece.** A circuit breaker only counts a call as failed once
  it *returns* a failure — an unbounded call with no timeout that hangs forever never contributes
  to the failure count and never trips the breaker; it just quietly consumes a connection/thread
  indefinitely. Every outbound call needs an explicit timeout shorter than the caller's own SLA
  budget, independent of and complementary to the circuit breaker.

---

## 3. Saga Pattern

- **The problem it solves**: a business transaction spanning multiple services (e.g., reserve
  inventory → charge payment → schedule shipment) cannot use a single ACID database transaction —
  each service owns its own data store, and distributed two-phase commit (2PC) across services is
  avoided in practice because it requires a coordinator holding locks across all participants for
  the duration, which does not scale and creates tight availability coupling (if any participant
  is down, all participants are blocked holding locks).
- **A saga is a sequence of local transactions, each with a corresponding compensating
  transaction** that semantically undoes it if a later step fails. Compensation is not a database
  rollback — "cancel the shipment" and "refund the payment" are new forward-moving business
  operations, not an undo — which means compensations must be designed as first-class,
  idempotent operations (Section 6) in each service, not an afterthought.
- **Choreography vs orchestration**: choreography has each service publish events and react to
  others' events with no central coordinator (loosely coupled, but the overall business process
  is implicit, scattered across services, and hard to observe/debug as a whole — "what's the state
  of order #123's saga?" requires stitching together logs from N services). Orchestration uses a
  central saga orchestrator that explicitly commands each step and handles compensation logic —
  easier to observe and reason about as a single state machine, but the orchestrator becomes a
  critical, stateful component that itself needs to be reliable and versioned carefully as the
  saga's steps change over time. Most production systems past a handful of steps end up moving
  toward orchestration specifically for the observability/debuggability win, even though
  choreography looks more "microservices-pure" initially.
- **Semantic locks**: because there's no real distributed lock across the saga's duration, an
  in-progress saga can leave a resource in a "pending" state visible to others (e.g., inventory
  marked `reserved` rather than `available` or `sold`) — this is a deliberate, explicit
  intermediate state design decision, not a technical lock, and needs to be part of the domain
  model from the start (this is exactly why the sealed-type domain modeling from Topic 1 matters —
  `Reserved`/`Sold`/`Cancelled` need to be real, distinguishable states).
- **Sagas are eventually consistent by nature** — there is a real time window where the system is
  in a partially-completed intermediate state. Any UI/API surfacing this state must account for it
  explicitly (e.g., "order pending confirmation") rather than assuming synchronous consistency.

---

## 4. Transactional Outbox

- **The dual-write problem it solves**: a service that writes to its own database *and* publishes
  a message to Kafka/a message broker as two separate operations cannot make both atomic — if the
  DB commit succeeds but the publish fails (crash, network partition), you've silently lost an
  event; if you publish first and the DB commit then fails, you've published an event for
  something that never happened. This is invisible in happy-path testing and shows up as subtle
  data-inconsistency incidents in production under real failure conditions.
- **The pattern**: write the business row *and* an outbox row (event payload, target topic,
  status) in the **same local database transaction** (a single-database ACID transaction, cheap
  and reliable), then a separate process reads the outbox table and publishes to the broker,
  marking rows as sent. This guarantees the event is never lost if the business write happened,
  and never claims an event happened if the business write didn't commit — atomicity is achieved
  entirely within one database, sidestepping the distributed dual-write problem.
- **Two ways to implement the relay**: a **polling publisher** (a scheduled job queries
  `WHERE status = 'PENDING'`, publishes, marks sent — simple, adds polling latency and DB load)
  or **CDC (Change Data Capture)** via a tool like Debezium reading the database's write-ahead log
  directly and streaming outbox inserts to Kafka with near-zero added latency and no polling load
  on the primary database — the trade-off is operational complexity (running and monitoring a CDC
  connector) versus simplicity.
- **This guarantees at-least-once delivery, not exactly-once** — a crash between publishing and
  marking the outbox row as sent will republish on recovery. **This means every consumer of these
  events must be idempotent** (Section 6) — the outbox pattern and idempotent consumers are a
  package deal, not independent choices.

---

## 5. CQRS (Command Query Responsibility Segregation)

- **The core idea**: separate the model used to handle writes (commands — enforces invariants,
  optimized for consistency and domain logic) from the model used to serve reads (queries —
  optimized for the actual shapes screens/APIs need, often denormalized). This is justified when
  read and write access patterns genuinely diverge — e.g., writes are narrow (create an order) but
  reads need wide, joined, aggregated views (an order history dashboard joining across orders,
  payments, shipments, and customer data) that would require expensive joins or N+1 queries
  against the normalized write model.
- **CQRS does not require event sourcing**, though they're commonly paired. A simpler and very
  common form: write to a normalized relational write-model, and asynchronously project changes
  into one or more denormalized read-optimized stores (a search index, a reporting table, a
  Redis-cached view) via the same outbox/event mechanism from Section 4. Event sourcing (storing
  the full sequence of domain events as the source of truth, rebuilding state by replaying them)
  is a separate, heavier decision with its own trade-offs (full audit trail, temporal queries, but
  higher complexity in versioning events and rebuilding projections) — don't reach for it just
  because you're doing CQRS.
- **The read model is eventually consistent with the write model** — there is a real, measurable
  lag between a command completing and its effects appearing in a read/materialized view. This
  must be surfaced honestly in the UX/API contract (e.g., returning the just-created resource from
  the command response itself rather than immediately querying the read model, which might not
  have caught up yet) — a very common CQRS bug is a client that writes, then immediately reads and
  gets stale/missing data, and mistakes it for a bug rather than an inherent property of the
  architecture.
- **When *not* to use CQRS**: if read and write models are basically the same shape (most CRUD
  services), CQRS adds a second model, a synchronization mechanism, and eventual-consistency
  complexity for no real benefit — it is a targeted answer to a specific scaling/complexity
  problem, not a default architecture.

---

## 6. Idempotency

- **Why it's non-negotiable in a distributed system**: any at-least-once delivery mechanism
  (message brokers after a rebalance, HTTP retries after a timeout where the server actually
  processed the request but the response was lost, the outbox pattern above) *will* redeliver
  messages/requests. A consumer/endpoint that isn't idempotent will double-charge a payment,
  double-ship an order, or double-decrement inventory — this is one of the most common real-world
  production incidents in payment/order systems specifically because it's invisible until the
  exact failure/retry timing lines up.
- **Natural idempotency vs. required application-level dedup**: `PUT /orders/123 {status: "shipped"}`
  is naturally idempotent — applying it twice yields the same end state. `POST /payments {amount:
  50}` is **not** naturally idempotent — calling it twice charges twice, because POST semantically
  means "create a new thing" each time. For any operation with real-world side effects triggered
  via POST (or any message consumer), you need an **explicit idempotency key**: a client-supplied
  or deterministically-derived unique key (e.g., `orderId + paymentAttemptNumber`) that the server
  checks against a store of already-processed keys before executing the side effect.
- **Idempotency key store design**: needs to be checked and recorded **atomically** with the
  side effect it's guarding — checking "have I seen this key" in Redis and then doing the payment
  charge as two separate steps has the exact same race condition the pattern is meant to solve
  (two concurrent requests can both pass the check before either records the key). The correct
  pattern is a unique constraint on the idempotency key column in the *same database transaction*
  as the business write (`INSERT ... ON CONFLICT DO NOTHING` in Postgres, or a unique index that
  makes the second insert fail, which the application catches and returns the original result
  for) — Redis alone is fine as a fast pre-check/optimization but shouldn't be the sole source of
  truth for something as consequential as "did we already charge this payment."
- **Idempotency keys need a retention/expiry policy** — keeping every key forever is unbounded
  growth; keeping them too briefly reopens the double-processing window for legitimately delayed
  retries (e.g., a client retrying after a long network partition). The window should be sized to
  the maximum realistic retry delay in the system (often driven by message broker retry/DLQ
  policies — this connects directly to Topic 5's Kafka retry/DLQ configuration).
- **Consumer-side idempotency for Kafka specifically**: partition-level ordering plus tracking the
  last-processed offset (or a business-level dedup key stored per consumer) handles redelivery
  after a rebalance or consumer crash before committing an offset — this is covered in depth with
  Kafka's own exactly-once semantics in Topic 5, but the underlying idempotency-key discipline
  here is the same discipline that makes Kafka's "effectively exactly-once" claims hold in practice.

---

## Interview-depth Q&A

1. Why is a synchronous, aggregating API gateway call to 4 downstream services a latency and
   availability risk even if each individual downstream service has a 99.9% SLA?
2. Walk through why a retry policy without backoff/jitter can turn a brief blip in one service
   into a cascading outage across a cluster, even with a circuit breaker in place.
3. Choreography-based sagas are often described as "more microservices-native" than orchestration.
   Why might a team still choose orchestration, especially past 4–5 saga steps?
4. Explain the dual-write problem precisely: what exact sequence of events causes a lost or phantom
   event, and how does the outbox pattern make it structurally impossible?
5. A team implements CQRS and then gets a bug report: "I created an order and it's missing from my
   order history page." What's actually happening, and how do you fix the UX without breaking the
   architecture?
6. Your idempotency-key check is in Redis, and the actual payment charge is a separate call to a
   payment provider. Under what exact race condition does this double-charge a customer, and how
   do you close it?

## Proof of learning
_Write one paragraph on a duplicate-processing, cascading-failure, or partial-saga-failure
incident you've seen or can imagine in your own systems — which single pattern here would have
prevented it, and what would you have had to change to adopt it?_
