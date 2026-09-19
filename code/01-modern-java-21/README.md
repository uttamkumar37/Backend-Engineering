# Code Progression — Topic 1: Modern Java 21

Companion code for [topics/01-modern-java-21.md](../../topics/01-modern-java-21.md). Each level
is runnable directly with `java <File>.java` (JDK 21 single-file source launch) unless noted.

## Beginner (`01-beginner/`)

Syntax-level familiarity — what a fresher needs to read and write these features at all.

### Records.java
A plain `record` with no validation — just the accessor/equals/hashCode/toString generation.

### SealedTypes.java
A minimal sealed interface with two permitted records and a `switch` that just prints each case.

### TextBlocks.java
A text block used for a multi-line string, nothing dynamic.

### ThreadsBasics.java
Creating one platform thread and one virtual thread and printing which thread ran — the smallest
possible demonstration that virtual threads exist and are created differently.

## Intermediate (`02-intermediate/`)

Applying the feature correctly — validation, deconstruction, and measuring a real trade-off.

### ValidatedRecords.java
Compact constructors enforcing invariants, plus the defensive-copy fix for a record holding a
mutable field (`List`) — the gap between "records are immutable" and "records are immutable
containers," from the concept doc's Section 1.

### RecordPatternMatching.java
Exhaustive `switch` with record deconstruction patterns and a guarded case (`when`), matching
the concept doc's Section 3.

### VirtualThreadBenchmark.java
The platform-vs-virtual-thread throughput benchmark for 10,000 blocking tasks, from the concept
doc's Section 4 — run it and read the actual numbers, don't just take the theory on faith.

## Advanced / Senior (`03-advanced/`)

Design judgment — using the features to make invalid states unrepresentable, and understanding
the concurrency model's sharp edges.

### OrderDomainModel.java
A sealed `OrderStatus` hierarchy plus an `Order` aggregate whose methods (`markPaid`,
`markShipped`, `cancel`) are the *only* way to change state, and reject invalid transitions. This
is the actual capstone-seed domain model referenced throughout later topics (the outbox, saga, and
idempotency discussions in Topic 4 all assume a domain model shaped like this).

### VirtualThreadPinningDemo.java
Reproduces the pinning problem from the concept doc's Section 4: a `synchronized` block blocking
inside a virtual thread pins it to its carrier. Run with
`-Djdk.tracePinnedThreads=full` to see the JVM report the pinned frame, then compare against the
`ReentrantLock` version, which doesn't pin.

### StructuredConcurrencyDemo.java
Uses `StructuredTaskScope.ShutdownOnFailure` (preview in JDK 21 — run with `--enable-preview
--source 21`) to fork two subtasks and show that a failure in one cancels the other automatically,
instead of manually managing a list of futures.

## How to use this progression
Read the beginner file, run it, then read the matching section in the concept doc before moving to
intermediate — the code alone doesn't explain *why*; the concept doc's internals discussion is
what turns "I can write this" into "I know when and why to reach for this."
