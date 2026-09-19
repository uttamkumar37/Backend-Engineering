# Code Progression — Topic 10: System Design & LLD

Companion code for [topics/10-system-design-lld.md](../../topics/10-system-design-lld.md). Pure
Java, no infrastructure — run any file directly with `java <File>.java` (JDK 21 single-file launch).

## Beginner (`01-beginner/`)

- **OpenClosedDiscountDemo** — the Strategy pattern as a direct realization of Open/Closed:
  `PriceCalculator` never changes when a new discount type is added.
- **StatePatternTrafficLightDemo** — the GoF State pattern via a sealed interface (Topic 1):
  each state is its own type, transitions are explicit, and the compiler enforces exhaustive
  handling.

## Intermediate (`02-intermediate/`)

- **ConsistentHashingDemo** — the concept doc's Section 3 claim, measured precisely on 10,000
  keys. Confirmed: naive `hash(key) % N` remapped **79%** of keys when going from 4 to 5 nodes;
  consistent hashing (100 virtual nodes per physical node) remapped only **19%** — almost exactly
  the theoretical minimum of 1/5 (20%) for adding one node to a five-node ring.
- **LruCacheDemo** — HashMap + doubly linked list giving true O(1) `get`/`put`. Confirmed:
  inserting a 4th item into a capacity-3 cache evicted exactly the least-recently-used entry, not
  the oldest-inserted one (accessing an item moves it to the front, changing what "least recently
  used" means).
- **TokenBucketRateLimiterDemo** — confirmed: a burst of 5 requests against a capacity-5 bucket
  all succeed immediately; the 6th–8th are rejected; after a 1-second wait (2 tokens/sec refill),
  exactly 2 more succeed before rejection resumes.

## Advanced (`03-advanced/`)

- **DebtSimplificationDemo** — the actual hard part of a Splitwise-style system, per the concept
  doc: not the CRUD entities, but settlement. A greedy net-balance-matching algorithm (repeatedly
  pairing the biggest creditor with the biggest debtor via two priority queues) confirmed reducing
  3 original pairwise expenses to **2 settlement transactions** — and the gap between "one
  transaction per expense" and the simplified minimum widens fast as group size grows.
- **ParkingLotDemo** — vehicle-size polymorphism plus a sealed-type `SpotState` (Free/Occupied),
  smallest-fitting-spot-first allocation. Confirmed: a second truck correctly fails to park once
  the only truck-sized spot is taken, since a truck can't fit in a car or motorcycle spot — the
  same Open/Closed discipline from the beginner tier holds up in a less trivial domain.

## How to use this progression

Run `ConsistentHashingDemo` and compare the 79% vs 19% remap numbers directly against the concept
doc's Kafka-partition and MongoDB-shard-key discussions — it's the same underlying math showing up
in three different systems.
