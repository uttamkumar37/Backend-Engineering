# Topic 3 — Data Layer: SQL Tuning, JPA/Hibernate, Redis, NoSQL

Assumes you can already write queries and `@Entity` classes. This is about what happens under a
query plan, why Hibernate's defaults quietly hurt you at scale, and when to reach for Redis or a
document store instead of "just another table."

---

## 1. SQL tuning & indexing

- **B-tree indexes, the default for a reason**: ordered structure, so equality *and* range
  queries (`>`, `<`, `BETWEEN`, `LIKE 'prefix%'`) are efficient; `LIKE '%suffix'` cannot use a
  B-tree at all (no leading anchor) — that needs a trigram (`pg_trgm`) or full-text index.
- **Composite index column order matters and is not symmetric.** An index on `(tenant_id, status,
  created_at)` serves queries filtering on `tenant_id` alone, `tenant_id + status`, or all three
  (leftmost-prefix rule) — but a query filtering only on `status` cannot use this index
  efficiently at all. The rule of thumb: put the highest-selectivity equality column first, then
  other equality columns, then the range/sort column last (so the index also serves `ORDER BY`
  without a separate sort step).
- **Selectivity, not existence, decides if the planner uses your index.** An index on a boolean
  `is_deleted` column with 99% `false` is nearly useless for `WHERE is_deleted = false` — the
  planner correctly chooses a sequential scan because a bitmap/index scan touching most of the
  table is slower than just scanning it. This is why "I added an index and the query is still
  slow" is often the planner being *right*, not broken — check `EXPLAIN (ANALYZE, BUFFERS)` for
  the actual row estimate vs actual rows; a large mismatch means stale statistics
  (`ANALYZE tablename`), not a missing index.
- **Covering / index-only scans**: if all columns a query needs (`SELECT`, `WHERE`, `ORDER BY`)
  are present in the index itself (via `INCLUDE` in Postgres, or just being part of the key), the
  engine can answer entirely from the index without touching the heap/table — a large win for
  hot read paths. This is the mechanism behind most "denormalize into a covering index" tuning wins.
- **Indexes aren't free**: every index is maintained on every `INSERT`/`UPDATE`/`DELETE` touching
  its columns. A table with 8 indexes to support every ad-hoc admin query pays that cost on every
  write — a common root cause of "writes got slower after we added reporting indexes." Measure
  write amplification, not just read speedup, before adding an index to a hot write table.
- **MVCC and why `UPDATE`-heavy Postgres tables bloat**: Postgres never updates a row in place —
  it writes a new row version and marks the old one dead, relying on `VACUUM` to reclaim space.
  A table with frequent updates and infrequent/misconfigured autovacuum accumulates dead tuples,
  degrading both index and scan performance over time even though row *count* looks stable. This
  is invisible until someone checks `pg_stat_user_tables.n_dead_tup`.
- **Isolation levels are about which anomalies you accept, not "how safe" in the abstract.**
  Postgres's default `READ COMMITTED` prevents dirty reads but allows non-repeatable reads and
  phantom reads within a transaction. `REPEATABLE READ` (Postgres implements this as snapshot
  isolation) prevents both but can raise serialization failures on concurrent writes to the same
  rows, which your application code must be prepared to retry. Choosing an isolation level is a
  concurrency/correctness trade-off, not a "more is always safer" dial — higher isolation
  increases abort/retry rates under contention.
- **Deadlocks**: almost always caused by two transactions acquiring the same two locks in opposite
  order (transaction A locks row 1 then wants row 2; transaction B locks row 2 then wants row 1).
  The database detects and kills one (deadlock victim); your code must retry it. The systemic fix
  is consistent lock acquisition ordering across all code paths (e.g., always lock by ascending
  primary key), not just retry logic papering over it.
- **Connection pool sizing (HikariCP) is not "bigger is better."** The formula from HikariCP's own
  guidance, `connections = ((core_count * 2) + effective_spindle_count)`, reflects that a
  database connection pool models available *CPU/IO parallelism* on the DB side, not application
  concurrency — an oversized pool causes more context-switching and lock contention on the DB than
  it relieves, and starves the DB of resources it needs for query execution itself. Size the pool
  from the database's capacity, then apply backpressure (queueing, virtual-thread-aware timeouts)
  on the application side rather than growing the pool to match request concurrency.

---

## 2. JPA/Hibernate pitfalls

- **The N+1 problem is a fetch-strategy default, not a Hibernate bug.** `@OneToMany` defaults to
  `FetchType.LAZY` (good), but `@ManyToOne`/`@OneToOne` default to `FetchType.EAGER` — a common
  surprise. Even with everything LAZY, iterating a list of parents and touching each parent's
  lazy child collection issues one query per parent. Fixes, in order of preference: a **DTO
  projection** query (skip entities entirely for read paths), `JOIN FETCH` in JPQL for a bounded,
  known-shape traversal, or an `@EntityGraph` when the same entity needs different fetch
  shapes per use case. `JOIN FETCH` on more than one collection in the same query causes a
  Cartesian product (row multiplication) — a second, subtler N+1-adjacent trap.
- **The persistence context (first-level cache) is why "I saved but the DB shows old data until I
  see a flush in the logs" happens** — Hibernate batches writes and flushes at transaction commit,
  an explicit `flush()`, or right before a query that could be affected by pending changes
  (auto-flush). Long-lived persistence contexts (e.g., **Open Session In View**, enabled by
  default in Spring Boot's `spring.jpa.open-in-view=true`) keep the session open through view
  rendering, which (a) silently allows lazy-loading in the presentation layer, hiding N+1 problems
  until production load reveals them, and (b) holds a DB connection for the entire
  request/response cycle including template rendering or JSON serialization. **Turn
  `open-in-view` off** in any service that isn't a legacy MVC app with server-rendered views, and
  fix the resulting `LazyInitializationException`s by choosing an explicit fetch strategy instead
  of relying on the accident of an open session.
- **Dirty checking cost scales with attached entity count.** At flush time, Hibernate compares
  every managed entity's current state against its loaded snapshot to decide what to `UPDATE`.
  A transaction that reads 10,000 entities into the context (e.g., a batch job) and mutates a
  handful still pays the comparison cost for all 10,000 — `setReadOnly` at the query/session
  level, or `StatelessSession` for pure batch processing, avoids this.
- **Entity `equals`/`hashCode` on JPA entities is a classic trap.** Using all fields (like a
  record would) breaks the moment a lazy proxy is involved (proxy fields aren't loaded, so
  equality is wrong) and changes identity as mutable fields change while the entity sits in a
  `HashSet`. The safe pattern is to base equality on the business/natural key if one exists, or
  fall back to comparing the database ID only when it's non-null (transient/new entities with
  `null` IDs are never equal to each other via ID — implement this deliberately, don't autogenerate
  it from an IDE template that includes every field).
- **Cascade types are about entity lifecycle propagation, not "convenience for saving nested
  objects."** `CascadeType.REMOVE`/`ALL` on a `@OneToMany` will delete child rows when the parent
  is deleted — correct for a true composition (`Order` → `OrderLine`), catastrophic if applied to
  an association that's actually a reference to a shared/independent entity (`Order` → `Customer`).
  Model ownership explicitly; don't cascade "because it compiles."
- **Optimistic locking (`@Version`) vs pessimistic (`SELECT ... FOR UPDATE`)**: optimistic locking
  fails fast at commit time with `OptimisticLockException` when two transactions modified the same
  row — cheap, no locks held, but requires the caller to retry, and is wrong for workflows where
  losing work silently (without a retry path) is unacceptable (e.g., a UI form save with no retry
  logic just shows an error and loses the edit). Pessimistic locking blocks concurrent
  transactions from the point of the `SELECT ... FOR UPDATE`, guaranteeing no lost update but
  holding a DB row lock for the transaction's duration — dangerous to combine with a wide
  transaction boundary (Topic 2) since it multiplies lock hold time.
- **Batch inserts need explicit configuration to actually batch.** Even with
  `spring.jpa.properties.hibernate.jdbc.batch_size=50` set, Hibernate silently disables batching
  for entities with `GenerationType.IDENTITY` primary keys, because it must round-trip to the DB
  to get each generated ID before it can build the next insert. Use `SEQUENCE` (with
  a pooled/hi-lo optimizer like `hibernate_sequence` with `allocationSize`) if bulk insert
  throughput matters — this is a common "why isn't batching actually reducing round-trips"
  production surprise.

---

## 3. Redis

- **Pick the data structure for the access pattern, not just "cache = string".** Hash for an
  object with fields you update independently (avoids read-modify-write races on the whole blob);
  sorted set for leaderboards/rate windows (score = timestamp or count, range queries by score are
  O(log N)); set for membership checks/deduplication; list only for small bounded queues (it's
  O(N) for arbitrary index access, and unbounded lists risk memory blowup) — use **Streams** for
  an actual durable queue/log with consumer groups, not `LPUSH`/`BRPOP` lists, once you need
  at-least-once delivery semantics or multiple consumer groups.
- **Cache-aside is the default pattern** (read: check cache, miss → read DB → populate cache;
  write: write DB, then invalidate/update cache) — and invalidate-then-write-through inconsistency
  windows are a real correctness concern under concurrent writes: if you invalidate the cache
  *before* the DB write commits, a concurrent reader can repopulate the cache with the stale
  pre-write value, "resurrecting" old data until the next write. The safer order is DB write
  commits, *then* invalidate — accepting a small window of a stale cache hit rather than a
  resurrected value that persists until overwritten again.
- **Cache stampede ("dog-piling")**: when a hot key expires, many concurrent requests miss
  simultaneously and all hammer the DB to repopulate it. Mitigations: a short-lived lock/mutex
  around the repopulation (only one request rebuilds, others wait or serve stale), staggered TTLs
  with jitter (avoid many keys expiring in the same instant), or serving stale-while-revalidate
  (return the expired value immediately while one request refreshes it in the background).
- **Distributed locks (`SET key value NX PX ttl`) are not linearizable/fully safe by default.** A
  lock can expire while the holder is still working (GC pause, network delay) and a second client
  acquires it, leading to two clients believing they hold the lock simultaneously. The Redlock
  algorithm (multiple independent Redis instances, majority quorum) improves availability under
  node failure but is still debated (see Martin Kleppmann's critique) for use cases needing true
  correctness guarantees, not just "good enough" mutual exclusion — for anything where a double
  execution is a real financial/safety problem, use a database-backed lock with fencing tokens, not
  a bare Redis lock.
- **Eviction policy is a capacity-planning decision, not a default to leave alone.**
  `noeviction` (default) returns errors on writes once `maxmemory` is hit — correct for a
  primary data store, wrong for a pure cache, where you want `allkeys-lru` or `allkeys-lfu` so
  Redis evicts old cache entries instead of rejecting writes and breaking the app.
- **Persistence (RDB snapshots vs AOF log) is about recovery, not durability during normal
  operation** — Redis is in-memory first; RDB can lose the last snapshot interval of writes on a
  crash, AOF (especially `appendfsync everysec`) loses at most ~1 second. If Redis holds anything
  you can't afford to lose and can't cheaply rebuild from the source of truth, that's a signal it
  shouldn't be your source of truth at all.

---

## 4. NoSQL — document stores (MongoDB as the reference)

- **The real trade-off is normalization vs. atomic-document-boundary, not "schema vs.
  schemaless."** MongoDB *has* schema — it's just enforced by your application (or `$jsonSchema`
  validation) instead of the DB engine. The design question that actually matters: what data needs
  to be read/written together atomically and colocated for read performance (embed it in one
  document) versus what's independently updated/queried/large (reference it in another
  collection). Over-embedding (e.g., embedding an unbounded array of order history events inside
  a customer document) hits the 16MB document size limit and degrades update performance long
  before that limit, since MongoDB rewrites the whole document on update by default.
- **Single-document writes are always atomic; multi-document transactions exist (since 4.0,
  cross-shard since 4.2) but cost real throughput** — they hold locks across the participating
  shards/replicas for the transaction's duration, and MongoDB's own guidance is to prefer
  schema design (embedding, or careful multi-step compensating writes) over reaching for
  multi-document transactions as the default tool, unlike a relational mindset where
  multi-row transactions are cheap and idiomatic.
- **Read/write concern is the actual durability dial**, analogous to SQL isolation levels but
  distinct: `writeConcern: majority` waits for acknowledgment from a majority of replica set
  members before returning (durable against single-node failure, higher latency);
  `writeConcern: 1` (primary only) is fast but a primary failover before replication can lose the
  write. `readConcern: majority` avoids reading data that could later be rolled back after a
  failover; the default `local` read concern can, in rare failover scenarios, return data that
  gets rolled back. Choosing these per-operation (not just once globally) is a real senior-level
  skill — e.g., `majority` write concern for payment records, default for high-volume analytics
  events you can afford to lose a few of.
- **Sharding is about write/storage scale, not automatic query speedup.** Choosing a shard key is
  effectively permanent-ish (resharding is possible but heavy) and the wrong choice
  (monotonically increasing key like a timestamp or auto-incrementing ID) creates a "hot shard" —
  all new writes land on one shard while others sit idle, defeating the purpose of sharding
  entirely. A well-distributed shard key (hashed, or a naturally high-cardinality field like
  customer ID) is the single most consequential MongoDB schema decision at scale.
- **When to reach for a document store over Postgres at all**: genuinely variable/polymorphic
  schemas per record (e.g., an event log with wildly different payload shapes per event type),
  very high write throughput with simple access patterns, or a need for horizontal write scaling
  that a single Postgres primary can't give you without significant additional infrastructure
  (Citus, manual sharding). It is usually the wrong choice when you need multi-entity
  transactional consistency as your default pattern (financial ledgers, anything with the strict
  invariants that motivated Topic 1's domain-modeling discussion) — that's relational territory.

---

## Interview-depth Q&A

1. A composite index on `(status, tenant_id, created_at)` isn't being used for a query filtering
   on `tenant_id` and `created_at` but not `status`. Why, and how would you fix it without adding
   a second index?
2. Explain why turning off `spring.jpa.open-in-view` can turn a working page into a wall of
   `LazyInitializationException`s, and why that's actually the correct outcome to force a fix.
3. You have `@Version` optimistic locking on an entity and users report "my changes disappeared."
   What's actually happening, and how do you fix the UX, not just the exception?
4. Why does adding `hibernate.jdbc.batch_size` do nothing for a table using
   `GenerationType.IDENTITY`, and what would you change?
5. A hot Redis key just expired under heavy load and your DB CPU spiked to 100% for 4 seconds.
   Name two different mitigations and the trade-off of each.
6. Why is a monotonically increasing `_id` a bad MongoDB shard key even though it seems like the
   most natural choice for time-series data?

## Proof of learning
_Write one paragraph on a real N+1, deadlock, cache-stampede, or hot-shard incident from your own
experience — what would you have changed sooner after this?_
