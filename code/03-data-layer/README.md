# Code Progression — Topic 3: Data Layer

Companion code for [topics/03-data-layer.md](../../topics/03-data-layer.md). Real Postgres,
real Redis, real Spring Data JPA/Hibernate — every number in this README came from actually
running the demo, not from a claim taken on faith.

## Prerequisites

```bash
brew services run postgresql@16   # or use an already-running Postgres
brew services run redis           # or use an already-running Redis
createdb backendplan_demo
```

MongoDB demos (`advanced/MongoEmbedVsReferenceDemo.java`) require a separately running MongoDB
instance (`brew install mongodb-community` + `brew services run mongodb-community`, or Docker) —
**this one was not executed in the environment this was built in**, since no local MongoDB was
available. It's included because the concept doc's Section 4 covers document-store schema design,
but treat it as unverified until you run it yourself.

## Build once, then run any demo

```bash
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:$(cat cp.txt)"
java -cp "$CP" <fully.qualified.DemoClassName>
```

## Beginner (`beginner/`)

- **JdbcBasics** — plain JDBC against real Postgres: connect, create table, insert, query.
- **JpaCrudBasics** — Spring Data JPA CRUD with zero query code (`JpaRepository`).
- **RedisBasics** — plain Jedis: SET/GET, EXPIRE (watch a key actually disappear after its TTL),
  and hash operations.

## Intermediate (`intermediate/`)

- **ExplainAnalyzeDemo** — bulk-loads 200k rows into Postgres and runs real
  `EXPLAIN (ANALYZE, FORMAT TEXT)` queries. Confirmed live: a composite index
  `(status, tenant_id, created_at)` is used when the query hits its leftmost prefix, ignored the
  moment you filter on `tenant_id` alone, and the planner correctly **prefers a sequential scan**
  over the same index when the filtered value is low-selectivity (99.9% of rows) versus using the
  index instantly when the value is high-selectivity (0.1% of rows) — this is the concept doc's
  Section 1 selectivity claim, with real query plans and real cost/timing numbers.
- **NPlusOneDemo** — 5 authors with 3 books each (default `LAZY` `@OneToMany`), fetched then
  iterated. Real Hibernate statistics confirmed **exactly 6 prepared statements** (1 for authors +
  5 lazy loads) for what looks like straightforward code.
- **CacheAsideDemo** — a deliberately slow fake "database" behind a Redis cache-aside wrapper;
  measured a real ~200x latency difference between a cache miss and a cache hit.

## Advanced (`advanced/`)

- **FetchStrategyFixDemo** — the same 5-authors/3-books scenario as `NPlusOneDemo`, fixed with a
  `JOIN FETCH` JPQL query. Confirmed: **1 prepared statement**, not 6.
- **BatchInsertIdentityVsSequenceDemo** — inserts 3,000 rows with `GenerationType.IDENTITY` and
  3,000 with `GenerationType.SEQUENCE`, same `hibernate.jdbc.batch_size=50` in both cases.
  Confirmed: IDENTITY produced 3,000 individual prepared statements (batching silently disabled,
  307ms); SEQUENCE produced 62 statements — 60 real JDBC batches — in 52ms. This is the concept
  doc's Section 2 batching claim made concrete, not a rule to memorize.
- **CacheStampedeDemo** — 20 concurrent threads hitting an empty cache key: the unprotected path
  hit the "database" 20 times simultaneously; the `SET NX EX`-mutex-protected path hit it exactly
  once, with the other 19 callers waiting briefly instead.
- **MongoEmbedVsReferenceDemo** — embed-vs-reference schema design (order line items embedded,
  customer referenced). Not run in this environment (see Prerequisites above).

## How to use this progression

Run each demo and read its actual printed numbers before re-reading the matching section of the
concept doc — several of these results (the 6x batching speedup, the stampede count going from 20
to 1) are more convincing seen live than described in prose.
