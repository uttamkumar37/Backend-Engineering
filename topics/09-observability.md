# Topic 9 — Observability: Micrometer, Prometheus, Grafana, OpenTelemetry, Load Testing

Assumes you already expose `/actuator/prometheus` and have a Grafana dashboard somewhere. This is
about the measurement mistakes that make dashboards lie to you — cardinality explosions, averaged
percentiles, closed-model load tests — and the vocabulary (RED/USE, SLOs) that turns metrics into
something actionable.

---

## 1. The three pillars, and why none of them alone is enough

- **Metrics** answer "how much/how often, aggregated over time" — cheap to store, great for
  dashboards and alerting, but lose per-request detail (you can see error *rate* went up, not
  *which* requests failed or why). **Logs** answer "what exactly happened for this one event," with
  full detail but expensive to store/query at volume and hard to aggregate meaningfully across
  thousands of instances. **Traces** answer "where did the time go across this one request as it
  crossed service boundaries" — the only pillar that shows causality/latency breakdown across a
  distributed call chain. A production incident investigation typically moves metrics → traces →
  logs: a dashboard alert (metric) narrows down *when* and *what service*, a trace for a slow/
  failed request in that window shows *which downstream call* was the bottleneck, and the logs for
  that specific trace ID show *why*. **This is why trace-ID-to-log correlation (injecting the
  trace ID into every log line via MDC) is not optional tooling polish** — without it, moving from
  "this trace was slow" to "here are the relevant log lines" requires manual timestamp-and-hope
  correlation across services, which doesn't scale under real incident time pressure.

---

## 2. Micrometer — the metrics facade, and its sharpest edge

- **Micrometer is to metrics what SLF4J is to logging**: a vendor-neutral facade
  (`Counter`, `Gauge`, `Timer`, `DistributionSummary`) that different backends (Prometheus,
  Datadog, CloudWatch) implement — instrumenting application code against Micrometer's API avoids
  coupling business code to a specific monitoring vendor.
- **Meter type selection matters**: a `Counter` only increases (request counts, error counts) —
  Prometheus's `rate()` function is what turns a monotonically increasing counter into a
  meaningful "per second" rate for dashboards (Section 3). A `Gauge` represents a point-in-time
  value that can go up or down (current queue depth, active connections) — a common mistake is
  implementing something that should be a Gauge as a Counter that's manually incremented/
  decremented, losing the "current value" semantics a real Gauge (backed by a live reference,
  e.g., `queue::size`) provides for free. A `Timer` records both count and duration distribution
  for an operation in one meter — the right default for "how long did this take, and how often."
- **Cardinality explosion is the single most damaging real-world Micrometer/Prometheus mistake.**
  Every unique combination of tag *values* creates a distinct time series stored independently —
  tagging a metric with something unbounded or high-cardinality (a raw user ID, order ID, or full
  URL with path parameters instead of a route template) multiplies stored series by the number of
  distinct values ever seen, which can go from a few hundred time series to millions, degrading or
  crashing the Prometheus instance (memory exhaustion) and making the metric useless to query
  besides. **The fix is enforced at instrumentation time**: tag by bounded, low-cardinality
  dimensions (route template like `/orders/{id}`, HTTP method, status code class like `2xx`/`5xx`,
  a small fixed set of business categories) and never by an entity ID, free-text field, or
  anything with unbounded distinct values — Spring Boot's default HTTP server metrics already
  template the URI for exactly this reason, and overriding that behavior to include raw path
  variables is a common way teams reintroduce the problem themselves.
- **Percentiles computed client-side per instance and then averaged across instances are
  mathematically invalid** — averaging several p99 values from different instances is not the
  same as the actual p99 across all requests system-wide (it systematically underestimates the
  true tail, since a percentile isn't a linearly averageable statistic). The correct approach is
  **histogram buckets** (`publishPercentileHistograms=true` in Micrometer, exposing counts of
  requests falling into predefined latency buckets) that Prometheus aggregates *across all
  instances first*, computing the true global percentile from the combined bucket counts via
  `histogram_quantile()` in PromQL — this is the actual reason Prometheus's histogram type exists
  as a distinct meter shape rather than just letting each client report its own percentile.

---

## 3. Prometheus — pull model, rate() vs raw values, cardinality limits

- **Prometheus pulls (scrapes) metrics from a known set of targets on an interval** rather than
  services pushing to it — this makes Prometheus itself the source of truth for "is this target
  even reachable" (a failed scrape is itself a signal) and keeps instrumented services simple
  (just expose an endpoint, no need to know where to push or handle push failures/backpressure).
  **Short-lived batch jobs that don't live long enough to be scraped** need the **Pushgateway** as
  a deliberate exception to the pull model — a job pushes its final metrics there before exiting,
  and Prometheus scrapes the Pushgateway itself; using Pushgateway for long-running services
  defeats its purpose and reintroduces the coupling/backpressure problems the pull model avoids.
- **`rate()` vs a raw counter value**: a raw Prometheus counter's absolute value is rarely
  meaningful on its own (it's cumulative since process start, and **resets to zero on a process
  restart/redeploy**, which `rate()` correctly handles by detecting the reset and not reporting a
  nonsensical negative rate, while a naive `counter[t] - counter[t-1]` calculation would not).
  `rate()` computes a per-second average over a range window and should be the default way any
  counter is graphed or alerted on; `irate()` (instantaneous rate from just the last two data
  points) reacts faster to spikes but is much noisier — appropriate for high-resolution
  dashboards, not for alerting rules, where the smoothing `rate()` provides avoids flapping alerts
  on normal short-term noise.
- **Scrape interval is a real trade-off**, not a free "more resolution is better" dial — a shorter
  interval increases storage volume and query cost linearly and, combined with high cardinality,
  compounds the storage problem from Section 2; a longer interval smooths out and can hide brief
  but real spikes (a 30-second latency spike might barely register at a 1-minute scrape interval).
  Most production setups use 15–30s as a balance, with critical high-traffic services sometimes
  scraped more frequently.
- **Service discovery for scrape targets** (Kubernetes SD, EC2 SD, Consul SD) is what lets
  Prometheus track a dynamic, autoscaling fleet without manually maintaining a target list — a
  static target list in a Kubernetes environment with HPA (Topic 8) silently stops covering new
  pods the moment autoscaling adds them, a subtle monitoring gap that shows as "coverage looks
  fine" while actually only watching a shrinking fraction of the fleet.

---

## 4. Grafana — dashboards, and alerting on the right thing

- **Alert on symptoms (SLOs), not on every possible raw threshold.** Alerting on "CPU > 80%" pages
  someone for a condition that may not affect users at all (the service might handle 80% CPU fine
  under its current load); alerting on an **SLO burn rate** (e.g., "the 30-day error-rate budget
  for 99.9% availability is being consumed 10x faster than sustainable") ties the alert directly
  to user-facing impact and avoids both alert fatigue from noisy infrastructure-level thresholds
  and, in the other direction, missing a real user-facing degradation that doesn't happen to spike
  CPU (e.g., pure I/O wait, or the CPU-throttling-without-visible-CPU-usage case from Topic 8).
- **The RED method (Rate, Errors, Duration) is the standard shape for a service-level dashboard**:
  request rate, error rate (as a percentage/ratio, not raw count — a raw error count means nothing
  without knowing total volume), and duration (as percentiles, per Section 2, not an average). Any
  service dashboard missing one of these three is missing a load-bearing signal, not just "nice to
  have."
- **The USE method (Utilization, Saturation, Errors) is the standard shape for a *resource*
  (CPU, a connection pool, a disk, a thread pool)** — utilization (how busy), saturation (how much
  work is queued waiting, which utilization alone doesn't show — a resource can be at 70%
  utilization with a large and growing queue, meaning it's actually failing to keep up), and
  errors (resource-specific failures, e.g., connection pool exhaustion errors). Applying USE to a
  HikariCP connection pool specifically (active connections, pending threads waiting for a
  connection, connection acquisition timeouts) is exactly the kind of dashboard that would have
  surfaced the connection-pool-exhaustion scenarios discussed in Topics 2 and 3 before they became
  an incident.
- **Dashboards as code** (Grafana dashboards defined in JSON/Jsonnet and version-controlled,
  provisioned via Terraform/the Grafana API rather than hand-edited in the UI) prevents dashboard
  drift and loss — a hand-built dashboard that only exists in one Grafana instance's database is a
  single point of institutional-knowledge loss the moment that instance is lost or migrated.

---

## 5. OpenTelemetry — unifying instrumentation, and the sampling decision that matters most

- **OpenTelemetry (OTel) is the vendor-neutral standard for traces, metrics, and logs together**,
  with an **OTel Collector** as a pipeline component that receives telemetry from instrumented
  services and exports it to whatever backend(s) an organization uses (Prometheus, Jaeger,
  Datadog, etc.) — this decouples instrumentation from backend choice the same way Micrometer does
  for metrics specifically, but across all three pillars and with standardized context propagation.
- **Auto-instrumentation (a Java agent attached at startup) covers common frameworks (Spring,
  JDBC, Kafka clients, HTTP clients) with zero code changes** — a reasonable default baseline, but
  it only captures spans at the boundaries the agent knows about; **manual spans are still needed
  for meaningful custom business logic boundaries** (e.g., "the fraud-check evaluation step" inside
  a larger request) that auto-instrumentation has no way to know is a meaningful unit to trace
  separately.
- **Context propagation across service boundaries relies on the W3C `traceparent` HTTP header**
  (and equivalent for messaging — trace context propagated through Kafka message headers) —
  without every hop propagating this header correctly (a service that doesn't forward it, or a
  hand-rolled HTTP client that strips unknown headers), the trace breaks into disconnected
  fragments per service instead of one coherent end-to-end trace, which is a very common
  "why does my trace stop at this service" debugging session.
- **Sampling strategy is the single highest-leverage OTel decision for cost vs. usefulness.**
  **Head-based sampling** (deciding to sample a trace at its very start, e.g., "keep 1% of all
  traces randomly") is cheap and simple but — critically — **a uniform random sample is very
  likely to miss the rare slow or error traces that actually matter for debugging**, since those
  are by definition a small minority of all traffic; you can easily end up with a trace store full
  of uninteresting fast successful requests and none of the incidents you'd actually want to
  investigate. **Tail-based sampling** (buffering all spans for a trace until it completes, then
  deciding to keep it based on outcome — e.g., always keep traces with errors or latency above a
  threshold, sample the rest at a low rate) directly targets the traces with actual investigative
  value, at the cost of needing to buffer complete traces before the sampling decision (more
  memory/complexity in the collector pipeline, and it requires all spans of a trace to route
  through a collector capable of making that buffered decision, which has real infrastructure
  implications for a high-traffic, multi-service system). Choosing head-based sampling "because
  it's simpler" for a system where debugging rare tail failures matters is a decision that quietly
  removes your ability to do exactly that.

---

## 6. Load testing — closed vs. open system models, and what to actually measure

- **The most consequential and least understood load-testing mistake is using a closed-system-
  model tool/configuration for a system that behaves as an open system in production.** A
  **closed model** (a fixed number of virtual users, each waiting for a response before sending
  its next request — the default behavior of many JMeter thread-group configurations) has a
  **self-limiting property**: if the system under test slows down, each virtual user simply sends
  requests less often, which mechanically caps the achievable request rate and **hides the actual
  latency degradation and queueing behavior** a real system experiences — real users/clients don't
  wait for your service to respond before deciding whether to make their *next unrelated* request;
  new requests keep arriving at roughly the same external rate regardless of how slow you are
  (an **open model** / constant-arrival-rate). Load testing with a closed model against a system
  that's actually open-model in production **systematically underestimates how bad a real
  overload incident would be**, because the test tool itself throttles its own offered load in
  response to the slowdown, precisely the opposite of what happens in reality. Tools like Gatling
  and k6 support open/constant-arrival-rate injection profiles explicitly for this reason, and
  choosing that model (not just "more virtual users") is the correct fix, not a tooling detail.
- **Measure percentiles (p95/p99/p999), never the average, as the primary result.** An average
  latency can look perfectly healthy while a meaningful fraction of real users experience multi-
  second responses — this is the load-testing analog of Section 2's percentile-averaging mistake,
  and for the same underlying reason: latency distributions are typically long-tailed, and an
  average is dominated by the (uninteresting) bulk of fast requests while hiding exactly the tail
  behavior that determines real user-perceived quality and SLA compliance.
- **Realistic traffic shape matters more than raw peak throughput numbers.** A load test that
  ramps smoothly to a target RPS and holds steady rarely resembles a real traffic pattern (which
  has bursts, diurnal cycles, and correlated spikes from e.g. a marketing campaign or a batch job
  firing at the top of the hour) — a test designed to find the actual breaking point should
  include burst/spike scenarios, not just sustained-load validation against an assumed SLA target,
  since the interesting failure modes (connection pool exhaustion, cascading circuit-breaker trips
  from Topic 4, GC pause pile-ups from Topic 1) are often burst-triggered rather than steady-state.
- **The goal of a real load test is to find the breaking point and the failure mode, not just to
  confirm the system meets today's expected load.** A test that stops as soon as the SLA target is
  hit tells you nothing about *how* the system degrades past that point (graceful queueing and
  backpressure vs. a sudden cliff into cascading failure) — which is exactly the information
  needed to size resource limits, circuit breaker thresholds, and autoscaling policies correctly
  in the first place.

---

## Interview-depth Q&A

1. A team tags an HTTP request duration metric with the raw request path (including path
   variables) instead of the route template. What happens to their Prometheus instance under real
   traffic, and why?
2. Explain why averaging each instance's locally computed p99 latency across 20 instances gives a
   materially wrong answer, and what the correct alternative is.
3. Why does `rate()` handle a counter reset from a pod restart correctly while a naive
   current-minus-previous calculation would not?
4. A dashboard shows CPU utilization at 40% but users report severe slowness. What USE-method
   signal would you check next, and what would explain the gap?
5. Why would head-based random trace sampling at 1% likely fail to capture the trace for a rare,
   intermittent 30-second latency spike affecting 0.1% of requests?
6. A JMeter load test with 200 fixed threads shows the system "handling load fine" up to a point,
   then throughput plateaus with no visible latency increase. What's the most likely explanation,
   and what would a corrected test setup show instead?

## Proof of learning
_Write one paragraph on a monitoring blind spot, a cardinality incident, or a load test that gave
a misleadingly reassuring result in your own experience — what would you measure differently now?_
