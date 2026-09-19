# Code Progression — Topic 5: Kafka

Companion code for [topics/05-kafka.md](../../topics/05-kafka.md). Real Kafka (KRaft mode, no
Zookeeper), plain `kafka-clients` — no Spring Kafka wrapper, so every guarantee (or lack of one)
is visible directly in the client API calls.

## Prerequisites

A local Kafka broker in KRaft mode (installed via `brew install kafka`; the Homebrew formula's
`server.properties` already has `process.roles=broker,controller` set for combined mode):

```bash
CLUSTER_ID="$(kafka-storage random-uuid)"
kafka-storage format --standalone -t "$CLUSTER_ID" -c /opt/homebrew/etc/kafka/server.properties
kafka-server-start /opt/homebrew/etc/kafka/server.properties &
```

Each demo creates its own topic(s) via `AdminClient` on first run, so no manual topic setup is needed.

## Build once, then run any demo

```bash
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:$(cat cp.txt)"
java -cp "$CP" <fully.qualified.DemoClassName>
```

## Beginner (`beginner/`)

- **ProducerBasics** — send 5 records, print the partition/offset each landed at.
- **ConsumerBasics** — subscribe, poll, print what comes back.
- **PartitionKeyDemo** — the same key sent 5 times always routes to the same partition; different
  keys spread across partitions. This is the mechanism behind per-entity ordering.

## Intermediate (`intermediate/`)

- **ManualOffsetCommitDemo** — a consumer processes message 3, then "crashes" before committing.
  A second consumer instance in the same group resumes from the last **committed** offset and
  reprocesses message 3 — a live demonstration of at-least-once delivery and why consumers must
  be idempotent, not a claim to take on faith.
  **Building this surfaced a real footgun worth keeping**: plain `consumer.commitSync()` with no
  arguments commits the consumer's *current position*, which already advances to the end of
  whatever batch `poll()` returned — not "the record my loop just finished." With multiple records
  per batch, that silently commits far more than intended. The fix (used here) is
  `commitSync(Map<TopicPartition, OffsetAndMetadata>)` with the specific record's offset + 1, and
  `max.poll.records=1` to make the batch boundary match the processing loop 1:1 for this demo.
- **ConsumerGroupAssignmentDemo** — 2 consumers in one group against a 4-partition topic; prints
  the actual partition assignment split, proving partition count is the real ceiling on
  parallelism within a group.

## Advanced (`advanced/`)

- **RetryTopicAndDlqDemo** — a "poison pill" message on the main topic is routed to a retry topic
  instead of blocking the partition; healthy messages keep processing. After exhausting retries,
  the poison message lands in a DLQ topic instead of looping forever.
- **ExactlyOnceTransactionalDemo** — a transactional producer doing read-process-write: one
  message's transaction commits, another's is deliberately aborted mid-transaction. A
  `read_committed` consumer on the output topic sees **only** the committed message — the aborted
  one is invisible, exactly as if it never happened. This is the real, narrow scope of Kafka's
  "exactly-once": atomic within Kafka, and only within Kafka.

## How to use this progression

Run each demo and read the actual partition/offset/DLQ output before re-reading the matching
section of the concept doc — the reprocessing in `ManualOffsetCommitDemo` and the vanishing
aborted message in `ExactlyOnceTransactionalDemo` are the two results most worth seeing live.
