# Topic 5 — Kafka: Producers, Consumers, Partitions, Retries, DLQ, Exactly-Once

Assumes you already produce/consume messages with Spring Kafka. This is about the guarantees
Kafka actually gives you (which are weaker and more nuanced than "it's a reliable queue"), and
where those guarantees end and your application code has to pick up the slack.

---

## 1. Partitions — the real unit of parallelism and ordering

- **Ordering is guaranteed only within a single partition**, never across partitions of a topic.
  If two events for the same entity (e.g., `order-123` created, then `order-123` paid) land in
  different partitions, a consumer can observe them out of order. This is why the **partition
  key** (usually the aggregate/entity ID) is a first-class design decision, not an afterthought —
  `producer.send(topic, key, value)` hashes the key to a partition, so the same key always routes
  to the same partition, giving per-entity ordering "for free" as long as the key is chosen
  correctly.
- **Partition count is your hard ceiling on consumer parallelism** — a consumer group can have at
  most as many *actively consuming* members (or threads, with multi-threaded consumers) as there
  are partitions; extra consumers beyond the partition count sit idle. This is why partition count
  is chosen for peak expected consumer parallelism, not current throughput.
- **Partition count is effectively a one-way door.** Kafka lets you increase partitions on a live
  topic, but this **breaks the key-to-partition mapping** for all existing keys (the hash mod
  changes), so any ordering guarantee for existing entities is lost at the moment of the
  increase — new messages for the same key can now land in a different partition than historical
  ones did. Decreasing partition count isn't supported at all without recreating the topic. This
  makes initial partition count a design decision worth real capacity planning, not a value to
  bump later without consequence.
- **A hot key skews load across partitions** — if one entity (e.g., a single high-volume tenant
  in a multi-tenant system) produces disproportionate traffic, its partition becomes a bottleneck
  regardless of how many partitions/consumers exist, since all of that entity's messages are
  pinned to one partition by design. This is a real, recurring production issue in multi-tenant
  event systems and has no clean fix other than accepting it, splitting the hot entity's stream
  differently, or over-provisioning that partition's consumer.

---

## 2. Producers

- **`acks` controls the durability/latency trade-off, not just "did it send."**
  `acks=0`: fire-and-forget, no durability guarantee, fastest, used only where losing messages is
  truly acceptable (e.g., non-critical metrics). `acks=1`: leader broker acknowledges after
  writing to its own log, but a leader crash before followers replicate loses the message — a
  common false sense of safety. `acks=all` (`-1`): leader waits for acknowledgment from all
  in-sync replicas (ISR) — the actual durability guarantee production systems need, at higher
  latency, and it must be paired with `min.insync.replicas` (e.g., 2) on the broker side, or
  `acks=all` with an ISR of just the leader itself still doesn't protect against that leader's loss.
- **The idempotent producer (`enable.idempotence=true`, default since Kafka 3.0 alongside
  `acks=all`) prevents duplicate messages from producer-side retries**, not from application-level
  redelivery. It works via a producer ID (PID) and per-partition sequence numbers the broker uses
  to detect and drop duplicate retries of the *same* in-flight batch — this closes the "network
  timeout caused a retry, but the original write actually succeeded" duplication window at the
  producer level, but it does **not** make your consumer logic itself idempotent against
  redelivery caused by consumer-side failures (Section 6/Topic 4's idempotency key discussion is
  still required for that).
- **Batching (`linger.ms`, `batch.size`) trades latency for throughput.** `linger.ms=0` sends as
  soon as possible (lowest latency, worst throughput/compression ratio); a small non-zero
  `linger.ms` (5–20ms) lets the producer accumulate a batch, dramatically improving throughput and
  compression effectiveness at a small, usually acceptable, latency cost — a very common and
  cheap tuning win that's frequently left at defaults unexamined.
  Compression (`compression.type=lz4` or `zstd`) is applied per-batch, so batching and compression
  compound — bigger batches compress better.
- **Producer buffer/backpressure**: `buffer.memory` bounds how much unsent data the producer
  holds in memory; when full, `send()` blocks up to `max.block.ms` then throws. Under a
  slow/unavailable broker, this is what prevents unbounded memory growth in the producer, but it
  also means a struggling Kafka cluster can make producing threads block — this needs to be
  accounted for in the same timeout/circuit-breaker thinking from Topic 4 if Kafka production
  happens synchronously inside a request path.

---

## 3. Consumers

- **Offset commits define your delivery semantics, and auto-commit is a footgun for anything
  that matters.** `enable.auto.commit=true` commits offsets on a timer, **independent of whether
  your message processing actually succeeded** — if processing throws after the auto-commit
  fires, that message is lost (marked consumed but never actually handled correctly): this is
  **at-most-once** behavior hiding inside what looks like a safe default. Manual commit *after*
  successful processing (`enable.auto.commit=false` + `ack.acknowledge()` in Spring Kafka's
  `MANUAL`/`MANUAL_IMMEDIATE` ack mode) gives **at-least-once** — a crash between processing and
  committing causes reprocessing of that message, which is why the consumer logic must be
  idempotent (Topic 4) rather than relying on Kafka alone to prevent duplicates.
- **The poll loop and `max.poll.interval.ms`**: a consumer is only considered alive if it calls
  `poll()` within this interval; long-running processing per batch (e.g., a slow synchronous
  external API call per message) that exceeds it causes the broker to consider the consumer dead
  and trigger a **rebalance mid-processing** — the classic symptom is a consumer that appears to
  process the same messages repeatedly under load, which is actually a self-inflicted rebalance
  loop, not a message-duplication bug. Fix by reducing `max.poll.records`, processing
  asynchronously outside the poll thread, or raising the interval deliberately once you've
  measured actual worst-case processing time.
- **Rebalancing itself pauses the whole consumer group** during the eager (default historically)
  protocol — every consumer gives up all partitions and reassignment happens from scratch, causing
  a visible processing stall proportional to group size. **Cooperative sticky rebalancing**
  (`CooperativeStickyAssignor`) incrementally reassigns only the partitions that need to move,
  keeping unaffected consumers processing throughout — this is a meaningful production upgrade for
  large consumer groups that rebalance frequently (e.g., due to rolling deployments), and it is
  not the default in all client versions, so it needs to be configured explicitly.

---

## 4. Retries and Dead Letter Queues

- **Blocking retry inside the poll loop stalls the whole partition** — if message processing
  retries synchronously (e.g., 3 attempts with sleep-based backoff) before returning from the
  handler, no other message on that partition can be processed until the retries finish or give
  up, and a burst of failing messages can cascade into the `max.poll.interval.ms` rebalance
  problem above. This is why **non-blocking retry via retry topics** is the standard production
  pattern: on failure, publish the message to a `topic-retry-1` (with a delay, often implemented
  via a scheduled/delayed redelivery mechanism or Spring Kafka's `RetryTopic` support), let the
  main partition keep moving, and have a separate consumer process the retry topic — with
  successive retry topics (`retry-1`, `retry-2`, `retry-3`) for increasing backoff, and a final
  DLQ topic after exhausting retries.
- **A dead letter queue is for messages that will never succeed without intervention** — a
  "poison pill" (malformed payload, a business rule that will always reject it, a permanently
  missing referenced entity). Routing these to a DLQ instead of retrying forever prevents them
  from blocking the partition indefinitely (an infinite-retry poison pill is functionally
  equivalent to the blocking-retry problem above, just spread out over retry topics rather than
  immediate blocking). **A DLQ without alerting and a defined remediation process is just a
  place where data quietly goes to die** — the pattern is incomplete without an owner and runbook
  for inspecting and either fixing-and-replaying or discarding DLQ messages.
- **Backoff between retry topic hops should be exponential with a cap**, matching the same
  retry-storm reasoning from Topic 4 — hammering a downstream dependency with retries at a fixed
  short interval defeats the purpose of spacing retries out to let transient failures (a brief DB
  outage, a downstream service redeploying) actually clear.

---

## 5. Exactly-once semantics — what it actually covers

- **"Exactly-once" in Kafka means exactly-once *within* Kafka** — specifically, the
  read-process-write pattern where a consumer reads from a topic, processes, and writes results
  to another Kafka topic, all wrapped in a **Kafka transaction** (`transactional.id` on the
  producer, `isolation.level=read_committed` on downstream consumers). The transaction atomically
  commits both the output message(s) *and* the input offset, so a crash mid-processing either
  commits both or neither — no output message will exist without its corresponding offset commit,
  eliminating the duplicate-output risk of plain at-least-once processing for this specific
  Kafka-to-Kafka pipeline shape.
- **This guarantee does not extend to external systems** — if the "write" step is actually a
  database write or an HTTP call to another service rather than a Kafka topic, Kafka's
  transactional guarantee stops at its own boundary. Achieving effectively-exactly-once semantics
  across a Kafka-to-database boundary requires combining Kafka's at-least-once delivery with the
  **idempotent consumer** pattern from Topic 4 (a unique constraint on a dedup key in the same DB
  transaction as the business write) — this is the practical, load-bearing pattern used in real
  payment/order systems, not Kafka's transactional producer/consumer feature alone, which is
  narrower than its name suggests.
- **Cost**: transactional producers add coordination overhead (a transaction coordinator broker
  round-trip, and `read_committed` consumers must buffer messages until the producing transaction
  commits or aborts, adding latency) — this is a real throughput/latency trade against plain
  idempotent (non-transactional) producing, and should be reached for specifically for the
  Kafka-to-Kafka read-process-write shape, not applied blindly everywhere "exactly-once" sounds
  desirable.
- **The idempotent producer (Section 2) and transactions are different features that compose** —
  idempotence prevents producer-retry duplicates; transactions add atomicity across multiple
  partitions/topics and the consumer offset commit. Enabling transactions requires idempotence to
  be enabled underneath it.

---

## 6. Operational reality: lag, monitoring, and common incidents

- **Consumer lag (the gap between the latest produced offset and the last committed consumed
  offset) is the primary health signal for a consumer group** — growing lag under steady traffic
  means the consumer is falling behind (undersized parallelism, a slow downstream dependency in
  the processing path, or a rebalance loop), not necessarily a Kafka problem. This connects
  directly to Topic 9 (Observability) — lag needs to be an alertable metric, not something
  discovered when a customer complains about stale data.
- **A "rebalance storm"** — repeated, frequent rebalancing — is almost always caused by consumers
  being killed/restarted too often (aggressive Kubernetes liveness probes killing a consumer mid-
  processing), `max.poll.interval.ms` being exceeded under load (Section 3), or session timeout
  misconfiguration relative to actual GC pause or processing latency characteristics — diagnosing
  it means correlating consumer group rebalance logs with deployment/restart events and processing
  latency percentiles, not just tuning Kafka client configs blindly.
- **Under-partitioned topics discovered late are expensive to fix** given the partition-increase
  ordering caveat in Section 1 — this is a strong argument for provisioning partition count with
  real headroom up front (informed by expected peak throughput ÷ target per-partition throughput,
  not current-day traffic) rather than treating it as an easy operational lever later.

---

## Interview-depth Q&A

1. Two events for the same order land out of order in a downstream consumer. What's the first
   thing you check about how the producer sends messages for that order?
2. Explain precisely why `enable.auto.commit=true` can silently lose messages, using the actual
   sequence of poll → process → commit-timer events.
3. A consumer group appears to reprocess the same batch of messages repeatedly under load, with no
   errors in the logs. What's your primary hypothesis, and how do you confirm it?
4. Why doesn't Kafka's exactly-once transactional producer protect you from double-charging a
   customer if the "write" step is a call to a payment gateway rather than another Kafka topic?
5. Design a retry/DLQ strategy for a payment-processing consumer that must never block healthy
   messages behind a handful of permanently failing ones. What are the topic(s) and backoff
   strategy involved?
6. A topic was created with 6 partitions two years ago and traffic has grown 10x. What are the
   real consequences of increasing it to 24 partitions today, beyond "it fixes the parallelism
   ceiling"?

## Proof of learning
_Write one paragraph on a real or plausible Kafka incident from your own systems — a rebalance
storm, a poison-pill message, or a duplicate-processing bug — and which specific configuration or
pattern here would have caught or prevented it._
