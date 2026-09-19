# Topic 1 — Modern Java 21: Internals, Concurrency, JVM/GC

Assumes you already write records, sealed classes, and switch expressions. This is about what
happens underneath, when each feature actually helps in production, and where it bites you.

---

## 1. Records — beyond the syntax

**What the compiler actually generates:** a `final` class extending `java.lang.Record`, private
final fields, a canonical constructor, accessor methods named after the fields (not `getX()` —
`x()`), and `equals`/`hashCode`/`toString` implemented via `invokedynamic` bootstrapped through
`ObjectMethods` (not naive reflection-free code you write yourself — it's a JVM-generated method
handle chain). This matters because:

- **equals/hashCode are structural, not identity-based**, and they're generated from *all*
  components, in declaration order. Adding a field silently changes equality semantics for every
  caller relying on it — a common source of cache-key or `Set`/`Map` bugs after a "harmless"
  field addition.
- **Records are effectively immutable containers, not immutable graphs.** If a record component
  holds a mutable type (`List<String>`, `Date`, an array), the record's own immutability is
  cosmetic — the compact constructor should defensively copy (`List.copyOf(...)`) and accessors
  should return unmodifiable views, or callers can mutate shared state through the record.
- **Records and JPA entities don't mix.** Hibernate needs a no-arg constructor, mutable fields
  for lazy loading/dirty checking, and proxying (CGLIB/bytebuddy subclassing) — all incompatible
  with a `final` class with only a canonical constructor. Use records for DTOs/read models/value
  objects, never for `@Entity`.
- **Records and Jackson**: works out of the box since Jackson 2.12+ via the `ParameterNamesModule`
  parameter introspection, but bean-validation annotations (`@NotNull`, `@Size`) must move onto
  the record's canonical constructor parameters, not fields — a frequent migration gotcha.
- **Compact constructors run before field assignment**, so you can normalize/validate but the
  fields you're validating are still the constructor parameters, not `this.x` yet. You cannot
  reference `this` before all fields are set — this is enforced by the compiler, unlike a regular
  constructor with early-return validation.

**When not to use a record:** anything needing inheritance (records can implement interfaces but
never extend a class, and can't be extended), builder-pattern construction with 6+ optional
fields (canonical constructor becomes unreadable — prefer a builder or default methods on a
companion), or JPA entities.

---

## 2. Sealed classes — why they exist beyond "restricted inheritance"

Sealed types exist to let the compiler prove *exhaustiveness*. This is the real payoff, and it's
what separates it from just "final-ish inheritance control":

- A `switch` over a sealed type's permitted subtypes with no `default` compiles only if every
  subtype is handled. Add a new permitted subtype later and **every switch statement over that
  type across the codebase fails to compile** until updated — this is a static, whole-program
  safety net an enum's `default: throw new IllegalStateException()` pattern can never give you,
  because that only fails at *runtime*, and only if that code path executes.
- This is the practical replacement for the classic Gang-of-Four **Visitor pattern** in Java: you
  used to use double-dispatch/visitor to get exhaustive handling of a closed type hierarchy
  without an `instanceof` chain; sealed + pattern matching gives you the same guarantee with far
  less boilerplate.
- **Sealed vs enum decision rule:** use an enum when all variants share the exact same shape (no
  variant-specific data) and you need `.values()`/`.ordinal()`/`EnumMap`/`EnumSet` performance
  characteristics (array-indexed, not hash-based). Use a sealed interface/class hierarchy when
  variants carry *different* data (e.g., `Cancelled(String reason)` vs `Shipped(String tracking)`)
  — this is a domain-modeling smell fix: teams often bolt a nullable `String reason` field onto
  every enum-based state class "just in case," which reintroduces the invalid-state problem
  records/sealed types are meant to eliminate.
- **Sealed hierarchies and modularity**: `permits` requires all subtypes to be in the same module
  (or same package if no module system), which is a deliberate constraint — sealed types are for
  closed, intentionally-designed domains, not for extensible plugin-style APIs (use a normal
  interface for those).

---

## 3. Pattern matching for switch — exhaustiveness mechanics and gotchas

- **Exhaustiveness checking is structural**: for a sealed type, the compiler statically enumerates
  `permits`. For non-sealed hierarchies you must supply a `default` or an unconditional pattern.
- **Case ordering matters** with guarded patterns (`case Paid p when p.amount().compareTo(BIG) > 0
  -> ...`): patterns are tested top-to-bottom like `instanceof` chains, so a broader unconditional
  pattern before a guarded one makes the guarded one unreachable — a compile error, but only if
  the compiler can prove it (it usually can for sealed types, not always for arbitrary guards).
- **Record patterns (nested deconstruction)**: `case Shipped(String tracking) when tracking.startsWith("EXP")`
  lets you deconstruct *and* guard in one case label; nested record patterns
  (`case Order(Customer(var name, var email), var status)`) avoid manual `.customer().name()`
  chains — but overusing deep nested deconstruction in a single switch arm hurts readability more
  than it saves; a good rule of thumb is nesting depth ≤ 2.
- **`null` handling changed**: switch on a reference type historically threw `NullPointerException`
  if the selector was `null`. Pattern-matching switches let you add `case null -> ...` explicitly;
  if you don't, the implicit NPE-on-null behavior is preserved for compatibility — a subtle trap
  if you assume pattern matching "handles null for you" by default.

---

## 4. Virtual threads — what's actually different at the JVM level

- **Mechanism**: a virtual thread is a `Continuation` (JVM-internal, not the public API) scheduled
  onto a small pool of **carrier threads**, which are ordinary platform threads, run by default on
  a `ForkJoinPool` in FIFO mode sized to `Runtime.availableProcessors()`. When a virtual thread
  blocks on a *supported* blocking operation (`Thread.sleep`, blocking I/O on NIO channels, `java.net`
  sockets, `java.sql` via JDBC drivers updated for it, `ReentrantLock`), the JVM **unmounts** the
  continuation from its carrier, freeing the carrier to run another virtual thread, and remounts it
  (possibly on a *different* carrier) when the operation completes.
- **This is why virtual threads help I/O-bound, not CPU-bound, work**: for CPU-bound work there's
  no blocking to unmount around, so you get the same throughput as platform threads but with the
  overhead of continuation bookkeeping — no benefit, occasionally a small regression.
- **Pinning**: a virtual thread stays mounted on its carrier (does *not* unmount) inside a
  `synchronized` block/method, or during a native method call / foreign function call. If that
  code then blocks (e.g., a `synchronized` method doing JDBC I/O with an old driver, or a
  synchronized block calling another blocking method), the carrier thread is blocked too — with
  only as many carriers as CPU cores, a handful of pinned virtual threads can starve the entire
  application. **Fix**: replace `synchronized` with `ReentrantLock` in hot paths that also do I/O;
  JDK 24 relaxed this (synchronized no longer pins in most cases), but on Java 21 it's a real
  production hazard. Diagnose with `-Djdk.tracePinnedThreads=full`.
- **Thread-locals still work but are expensive at scale**: virtual threads support `ThreadLocal`,
  but if you spawn millions of them, unbounded thread-locals (e.g., a `SimpleDateFormat` cache per
  thread) multiply memory linearly — prefer `ScopedValue` (JDK 21 preview) for structured,
  immutable, per-task context propagation instead.
- **Don't pool virtual threads.** `Executors.newVirtualThreadPerTaskExecutor()` is the idiomatic
  usage — one virtual thread per task, created and discarded cheaply (~a few hundred bytes vs ~1MB
  reserved stack for a platform thread). Pooling defeats the model and reintroduces contention.
- **Structured concurrency** (`StructuredTaskScope`, still preview in 21): ties a group of forked
  virtual-thread subtasks to a single lexical scope, so cancellation, error propagation, and
  shutdown of siblings happen automatically when the scope exits or one subtask fails
  (`ShutdownOnFailure`) — this replaces manual `CompletableFuture.allOf` + timeout + cancellation
  bookkeeping, and it fixes a real footgun: with plain futures, if one parallel call fails, the
  others keep running unless you explicitly cancel them, leaking threads and wasted work.
- **Spring Boot 3.2+ integration**: set `spring.threads.virtual.enabled=true` to make the embedded
  Tomcat use a virtual-thread-per-request executor. This helps only if your request-handling stack
  is otherwise blocking (JDBC, blocking `RestTemplate`/`WebClient.block()`) — it is *not* a
  replacement for reactive/WebFlux if you need true backpressure or bounded concurrency to a
  downstream system, since virtual threads don't limit concurrency by themselves (a downstream DB
  connection pool still bottlenecks you, and now more callers can queue up waiting on it).

---

## 5. JVM & GC tuning — what a senior engineer is expected to reason about

- **Generational hypothesis**: most objects die young. All mainstream collectors (G1, Parallel,
  ZGC, Shenandoah) exploit this with young/old generation splits (ZGC generational mode added in
  JDK 21 too) — minor GCs (young) are frequent and cheap, major/full GCs (old, or whole-heap) are
  rare and expensive.
- **G1 (default since JDK 9)**: region-based, aims for a **pause-time target** (`-XX:MaxGCPauseMillis`,
  default 200ms) rather than a fixed generation size ratio. It achieves this by picking the
  regions with the most garbage first ("garbage first"). Tuning is mostly about *not* fighting the
  pause-time model: set a realistic target, size the heap (`-Xms`=`-Xmx` to avoid resize pauses),
  and watch for **mixed GC** cycles falling behind (humongous allocations — objects >50% of a
  region size — bypass young-gen entirely and go straight to old gen, common with large byte
  arrays/protobuf messages, and can fragment the heap).
- **ZGC / Shenandoah**: sub-millisecond pause targets by doing marking and compaction concurrently
  with application threads, using colored pointers (ZGC) or Brooks pointers (Shenandoah) instead
  of stop-the-world compaction. Trade-off: higher CPU overhead and (for ZGC pre-generational)
  historically weaker throughput on allocation-heavy young-gen churn — JDK 21's generational ZGC
  closes much of that gap. Choose these over G1 when p99/p999 latency matters more than raw
  throughput (e.g., trading systems, real-time bidding) and you can afford the CPU headroom.
- **What actually causes GC pain in a Spring Boot service, in order of frequency seen in practice**:
  1. Unbounded caches (`ConcurrentHashMap` used as a cache with no eviction) — slow leak, shows as
     rising old-gen occupancy over days.
  2. Large request/response payloads deserialized fully into memory (Jackson on multi-MB JSON) —
     humongous allocations, G1 old-gen fragmentation.
  3. String concatenation/`+` in hot loops before JIT can prove it's safe to use `StringBuilder`
     internally — the JIT usually handles simple cases, but does not save you across method
     boundaries or in loops appending to a growing accumulator passed around.
  4. Autoboxing in numeric hot paths (`Map<Long, BigDecimal>` ledgers) — every boxed `Long`/`Integer`
     outside the small integer cache (-128..127) is a fresh heap object.
- **JIT tiers, briefly**: C1 (client compiler, fast to compile, less optimized) compiles hot methods
  first; C2 (server compiler, slower to compile, heavily optimized — inlining, escape analysis,
  loop unrolling) recompiles methods that stay hot. **Escape analysis** lets C2 stack-allocate
  objects that provably don't escape a method (common with small value-like objects created and
  discarded in a loop) — this is part of why microbenchmarking short-lived object allocation
  without JMH (which forces realistic JIT warmup) gives misleading numbers.
- **Practical tuning checklist for an interview or a real incident**: heap sizing (`-Xms=-Xmx`,
  avoid the OS swapping), pick a collector matching your latency/throughput priority, enable GC
  logging (`-Xlog:gc*:file=gc.log:time,uptime,level,tags`), correlate GC pause spikes with APM
  traces (this is where topic 9, Observability, connects back here), and always reproduce load
  with a profiler (async-profiler, JFR) before changing flags — tuning blind is how you end up
  with cargo-culted `-XX` flags nobody can explain.

---

## Interview-depth Q&A (write your own answers, then compare)

1. Why do records use `invokedynamic`-generated `equals`/`hashCode` instead of the compiler just
   emitting the bytecode directly, and does it matter for performance?
2. You have a sealed `PaymentEvent` hierarchy with 4 subtypes consumed across 12 microservices.
   Why is adding a 5th subtype a breaking change in a way an enum addition wouldn't be, and how do
   you manage that rollout safely?
3. A production service pinned virtual threads under load because of a `synchronized` block
   around a legacy JDBC call. Walk through how you'd detect this, prove it's the cause, and fix it
   without a full driver upgrade.
4. Why does `spring.threads.virtual.enabled=true` sometimes make P99 latency *worse* under a
   connection-pool-bound workload, not better?
5. You inherited a service doing G1 full GCs every few hours despite a normal-looking heap graph.
   What are the first three things you check, and why?
6. Explain structured concurrency's `ShutdownOnFailure` policy versus manually managing a list of
   `CompletableFuture`s with `allOf` — what class of bug does it eliminate?

## Proof of learning
_Write one paragraph, in your own words, after working through this — what changed in how you'd
design a domain model or size a JVM after reading this, not a restatement of the notes._
