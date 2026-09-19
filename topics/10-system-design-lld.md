# Topic 10 — System Design & LLD

This topic is structured differently from the others: it's less "here's what a tool does
internally" and more a framework for approaching design problems, plus the building blocks and
practice problems to actually get good at applying it. Everything in Topics 1–9 is the raw
material system design draws on — this topic is about combining them under real constraints and
communicating the trade-offs out loud, which is specifically what separates a senior-level design
interview performance from a mid-level one.

---

## 1. What actually changes between mid-level and senior-level expectations

- **A mid-level answer produces *a* working design. A senior-level answer produces a design plus
  an explicit account of what was traded away and why**, driven by requirements the candidate
  extracted, not assumed. Interviewers calibrate almost entirely on this: does the candidate ask
  about read/write ratio, consistency requirements, and scale *before* designing, or jump straight
  to drawing boxes? Does the candidate proactively name the weaknesses of their own design (single
  point of failure, a consistency window, a hot-key risk) rather than waiting to be asked?
- **There is deliberately no single correct answer** in most real system design problems — the
  evaluation is on the reasoning connecting requirements to decisions, not on recalling "the"
  canonical architecture for a URL shortener. Memorizing a specific design from a video without
  internalizing *why* each piece is there falls apart the moment the interviewer changes one
  constraint (e.g., "now assume 100x write volume" or "now assume strong consistency is required
  for this one field").
- **Driving the conversation matters as much as the content.** A senior engineer is expected to
  structure the session themselves (clarify requirements → estimate scale → sketch API/data model
  → high-level architecture → deep-dive on the 1–2 hardest components → discuss bottlenecks and
  trade-offs) rather than needing the interviewer to prompt each step — this structure exists
  because unstructured design discussions reliably run out of time on the easy parts and never
  reach the parts that actually differentiate candidates.

---

## 2. The framework, step by step

1. **Clarify functional and non-functional requirements explicitly, out loud.** Functional: what
   operations does the system support (post a tweet, shorten a URL, send a notification)?
   Non-functional: read-heavy or write-heavy, latency requirements, consistency requirements
   (can this tolerate eventual consistency, or does it need strong consistency — directly
   informed by Topic 3's isolation-level and Topic 4's saga/CQRS eventual-consistency discussion),
   availability target, expected scale.
2. **Back-of-envelope capacity estimation** — daily active users, requests per second (average and
   peak), storage growth per day/year, bandwidth. This isn't about precision; it's about
   discovering which resource (storage, read QPS, write QPS, bandwidth) is actually the
   constraining one, which determines where the design needs to spend its complexity budget. A
   design that spends most of its effort solving a dimension that back-of-envelope math shows is
   not actually the bottleneck is a signal the candidate skipped this step.
3. **API design** — the actual request/response contracts for the core operations, which forces
   concrete decisions (pagination strategy, idempotency keys for write operations per Topic 4,
   what's synchronous vs. what should be async/queued) before the architecture diagram papers over
   them.
4. **Data model** — what entities, what relationships, and critically, **which datastore fits the
   access pattern** (Topic 3's relational-vs-document trade-off, applied here): does this need
   joins and transactional consistency (relational), extreme write throughput with simple access
   (document/wide-column), or is it fundamentally a caching/session problem (Redis)?
5. **High-level architecture** — services, datastores, caches, queues, load balancers, arranged to
   satisfy the requirements from step 1, informed by the estimates from step 2.
6. **Deep dive into the 1–2 hardest components** — this is where most of the interview signal
   actually comes from; spending equal time on every box in the diagram instead of identifying and
   drilling into the genuinely hard part (e.g., "how do we guarantee exactly-once notification
   delivery," not "here's a load balancer") is a common way strong technical candidates
   underperform in this specific format.
7. **Bottlenecks, single points of failure, and trade-offs, stated proactively** — this is the
   step that most separates senior from mid-level performance, per Section 1.

---

## 3. Core building blocks (the vocabulary a design draws on)

- **Load balancers**: L4 (transport layer — routes based on IP/port, fast, protocol-agnostic, no
  visibility into HTTP content) vs. L7 (application layer — can route on URL path, headers,
  cookies; enables content-based routing, canary/blue-green traffic splitting from Topic 8, and
  terminates TLS) — choosing L4 when path-based routing or canary analysis is actually needed is a
  design gap, not a simplification.
- **CDN**: pushes static (and increasingly some dynamic, cacheable) content to edge locations near
  users, cutting latency and origin load — the design question is always "what's cacheable and
  for how long," and cache-invalidation strategy for content that changes (same fundamental
  problem as Topic 3's Redis cache-invalidation discussion, at a different layer).
- **Caching strategies at the architecture level**: cache-aside (Topic 3) is the most common;
  **write-through** (write to cache and DB synchronously, together) keeps the cache always
  consistent at the cost of write latency; **write-back/write-behind** (write to cache
  immediately, asynchronously flush to DB later) is fast but risks data loss on a cache failure
  before the flush — the choice depends on whether the data can tolerate that loss window, which
  is a requirements question from step 1, not a default to reach for.
- **Database scaling**: read replicas handle read-heavy load (with the same replication-lag
  eventual-consistency caveat from Topic 3's MongoDB `readConcern` discussion — a read from a
  replica can be stale); sharding handles write-heavy/storage-scale load, and **shard key
  selection has the exact same hot-shard risk discussed in Topic 3 and Topic 5** — this is a
  recurring theme across topics for a reason: it's the same underlying problem (uneven
  distribution of a partitioning key) appearing in Kafka partitions, MongoDB shards, and any
  horizontally-scaled datastore.
- **Consistent hashing** is the general solution to the "adding/removing a node reshuffles
  everything" problem that Topic 5 flagged for naive modulo-based Kafka partition changes — instead
  of `hash(key) % N` (where changing N remaps nearly every key), consistent hashing maps both
  nodes and keys onto a ring, so adding/removing one node only remaps the keys that were adjacent
  to it on the ring, not the whole keyspace. This is the mechanism behind most distributed
  caches' and load balancers' rebalancing behavior, and understanding it is what lets you explain
  *why* a system like DynamoDB or a Redis Cluster tolerates node changes gracefully while a naive
  hash-mod sharding scheme doesn't.
- **Message queues for decoupling** — the same reasoning as Topic 4/5: a queue between a
  fast producer and a slower/bursty consumer absorbs load spikes and lets each side scale/fail
  independently, at the cost of the eventual-consistency and idempotent-consumer obligations
  already covered in depth in Topic 4.
- **Rate limiting algorithms**: **token bucket** (tokens refill at a fixed rate, a request
  consumes a token, allows bursts up to the bucket size) is the most commonly used because it
  permits reasonable burstiness while enforcing a long-term average rate; **leaky bucket**
  smooths bursts into a strictly constant output rate (better for protecting a downstream system
  that truly can't handle any burst); **sliding window** (counting requests in a moving time
  window, sometimes approximated for efficiency) avoids the fixed-window edge case where two
  bursts straddling a window boundary both pass unthrottled. Choosing between these is a real
  design decision tied to what's being protected — a client-facing API and a fragile downstream
  legacy system want different answers.
- **CAP theorem, applied practically, not as trivia**: under a network partition, a system must
  choose between consistency and availability for the affected data — this isn't an abstract
  choice made once for an entire system, but often a per-operation one (Topic 3's MongoDB
  read/write concern is exactly this dial, applied at the operation level rather than the whole
  system). The practically useful framing in an interview is PACELC, not just CAP: **even absent
  a partition**, there's still a latency-vs-consistency trade-off (Else) — a synchronous
  multi-region write for strong consistency costs real latency compared to an asynchronously
  replicated one, independent of any partition scenario.
- **Distributed consensus (Raft/Paxos), at the level a backend engineer needs**: these algorithms
  exist to let a cluster of nodes agree on a single value/leader despite node failures and
  message delays, without split-brain (two nodes both believing they're the leader
  simultaneously) — you rarely implement this yourself, but recognizing that a datastore's leader
  election, a Kafka controller election, or a Kubernetes etcd cluster's behavior under a network
  partition is governed by one of these algorithms is what lets you reason correctly about a
  system's actual failure behavior during a partition instead of assuming naive behavior.

---

## 4. HLD practice problems, and what each one is actually testing

- **URL shortener**: encoding scheme choice (base62 counter vs. hash-based, and the collision/
  distributed-uniqueness problem this creates for hash-based approaches), read-heavy caching,
  and a good vehicle for practicing capacity estimation cleanly since the data model is simple.
- **Rate limiter (as a distributed system, not a single-process class)**: forces a real distributed-
  state discussion — where does the counter/token-bucket state live (a single Redis instance is a
  bottleneck and single point of failure at scale), and how do multiple API gateway instances
  share rate-limit state consistently without each one enforcing its own independent, incorrect
  limit.
- **Distributed cache**: consistent hashing, eviction policy, replication for availability, and
  the cache-stampede problem from Topic 3 at a system-design level rather than a single-service level.
- **Notification/messaging system** (push notifications, email, SMS fan-out): exercises the
  outbox pattern (Topic 4), idempotent delivery, retry/DLQ design (Topic 5), and prioritization/
  rate-limiting per downstream provider (a provider like an SMS gateway has its own rate limits
  the system must respect).
- **Payment system**: directly exercises idempotency keys (Topic 4), saga/compensating
  transactions for multi-step flows (reserve → charge → confirm), strong consistency requirements
  for ledger correctness (a place where eventual consistency is usually *not* acceptable, unlike
  most of the rest of the system), and audit/immutability requirements.
- **News feed / chat system**: fan-out-on-write vs. fan-out-on-read trade-off (precomputing each
  follower's feed on every post vs. computing a feed at read time by querying followed users' posts)
  — a direct, concrete instance of the read/write-pattern-divergence reasoning that motivates CQRS
  (Topic 4), and a good problem for practicing the "which side's requirements dominate the design"
  judgment call (a celebrity account with millions of followers breaks naive fan-out-on-write,
  which is why real systems like Twitter use a hybrid).

---

## 5. LLD — a different discipline, same underlying material

- **LLD interviews evaluate object-oriented design and extensibility, not scale** — the actual
  class structure, interface boundaries, and how cleanly the design accommodates a stated future
  requirement change, not throughput or availability. SOLID principles are the working vocabulary:
  Single Responsibility (a class doing one thing makes it independently testable — ties directly
  to Topic 7's point about mocking fewer, more focused collaborators), Open/Closed (extensible via
  new implementations rather than modifying existing code — the sealed-interface-plus-pattern-
  matching approach from Topic 1 is a direct, modern realization of this for closed domains),
  Liskov Substitution (a subtype must be usable anywhere its supertype is expected without
  surprising behavior — violated by, e.g., a `Rectangle`/`Square` inheritance relationship where
  setting width unexpectedly changes height), Interface Segregation (many small, focused
  interfaces over one large one client code is forced to partially implement or ignore), and
  Dependency Inversion (depend on abstractions, not concrete implementations — the same reasoning
  behind injecting an interface instead of calling a static method, referenced in Topic 7's
  discussion of why heavy static-method mocking is a design smell).
- **Design patterns that show up repeatedly in backend LLD problems, mapped to what they're
  actually for**: **Strategy** (interchangeable algorithms behind a common interface — e.g.,
  different pricing/discount strategies); **Factory** (centralizing object creation logic,
  especially when the concrete type to create depends on runtime input); **Builder** (constructing
  a complex object step by step, especially with many optional parameters — the classic
  alternative to a record's canonical constructor becoming unreadable, per Topic 1's records
  discussion); **Observer** (one-to-many event notification — the in-process analog of a message
  broker's pub/sub, useful for decoupling side effects from a core operation); **Decorator**
  (adding behavior to an object without modifying its class — used for layered request-processing
  concerns, similar in spirit to how a servlet filter chain composes cross-cutting behavior in
  Topic 6); **Chain of Responsibility** (a sequence of handlers, each deciding to process or pass
  along a request — a natural fit for a request-validation pipeline or the filter-chain model
  itself); **State** (an object's behavior changes based on its internal state, with each state as
  its own class — this is precisely the domain-modeling problem Topic 1's sealed `OrderStatus`
  hierarchy solves, and recognizing that a sealed-interface design *is* a modern take on the GoF
  State pattern is a strong signal in an LLD discussion).
- **Common LLD practice problems**: parking lot (spot allocation strategy, vehicle-type
  polymorphism — a clean Strategy/State pattern exercise), elevator system (state machine per
  elevator, scheduling algorithm — another strong State pattern fit), library management system
  (entity relationships and checkout/reservation state transitions), Splitwise/expense-sharing
  (the trickiest part is the settlement/debt-simplification algorithm, not the CRUD entities —
  a good test of whether a candidate identifies the actual hard problem instead of spending all
  their time on straightforward entity modeling), LRU cache (a focused data-structure design
  problem — doubly linked list + hash map — that also tests whether a candidate can reason about
  time complexity precisely, O(1) get/put being the actual requirement being tested, not just "a
  cache that evicts old things").

---

## Interview-depth Q&A

1. An interviewer changes a requirement mid-design from "eventual consistency is fine" to "this
   field must be strongly consistent." Walk through what actually changes in your architecture,
   not just that "you'd use a different database."
2. Why is fan-out-on-write a bad default for a social feed system with celebrity accounts, and
   what hybrid approach addresses it?
3. Explain consistent hashing precisely enough to describe why it limits the blast radius of
   adding a node, compared to `hash(key) % N`.
4. In a payment system design, where specifically would you apply the saga pattern versus a single
   local ACID transaction, and what's the deciding factor?
5. A candidate's LRU cache design uses a `HashMap` plus a `LinkedList` with O(n) removal from the
   middle. What's wrong with this, and what data structure combination actually achieves O(1) for
   both get and put?
6. Why does PACELC add something CAP theorem doesn't, and when does that distinction actually
   change a real design decision (not during a partition)?

## Proof of learning
_Pick one HLD and one LLD problem from Sections 4–5, design them fully using the framework in
Section 2, and write one paragraph on which trade-off in your design you're least confident about
and why._
