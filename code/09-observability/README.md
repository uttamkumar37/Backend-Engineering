# Code Progression — Topic 9: Observability

Companion code for [topics/09-observability.md](../../topics/09-observability.md). A real Spring
Boot + Micrometer app, real Prometheus and Grafana (run via Podman, images already cached
locally), and real k6 load tests — every number below came from actually running this stack.

## Beginner (`01-beginner/app/`)

```bash
cd 01-beginner/app
mvn spring-boot:run &     # app on :8090, /actuator/prometheus exposed
curl localhost:8090/orders/42
curl localhost:8090/actuator/prometheus | grep http_server_requests
```

**Confirmed live: the cardinality-explosion mistake, side by side with the safe default.**
The app exposes `GET /orders/{id}`, which (a) increments a **manually created** counter tagged
with the raw path variable (`order.id`), and (b) is automatically tracked by Spring's own
`http.server.requests` metric, tagged by the **route template** `/orders/{id}`. After 51 requests
with distinct IDs:
- The manual raw-ID-tagged counter: **53 distinct time series** (one per ID ever seen — unbounded).
- Spring's own templated metric: **1 series**, `http_server_requests_seconds_count{uri="/orders/{id}"} = 51`.

## Intermediate (`02-intermediate/`)

Real Prometheus (`docker.io/prom/prometheus:v2.55.1`, no fresh pull needed) scraping the app:

```bash
podman run -d --name demo-prometheus -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml:Z \
  docker.io/prom/prometheus:v2.55.1
```

Confirmed live:
- **`rate()` correctly survives a counter reset.** After restarting the app (the counter dropped
  from 71 back to 1), `rate(http_server_requests_seconds_count{uri="/orders/{id}"}[1m])` returned
  a small, sane `0.345` — not a nonsensical negative number a naive
  `counter[t] - counter[t-1]` calculation would have produced.
- **`histogram_quantile()` computes a true percentile from real buckets.** Hitting `/slow` with
  delays from 50ms to 3000ms, then querying
  `histogram_quantile(0.95, http_server_requests_seconds_bucket{uri="/slow"})` returned `3.04s` —
  correctly reflecting the tail of the actual distribution sent, computed server-side from bucket
  counts exactly the way Topic 9's concept doc describes as the fix for per-instance-averaged
  percentiles.

## Advanced (`03-advanced/`)

### Grafana wired to the same Prometheus (the full real stack, end to end)

```bash
podman run -d --name demo-grafana -p 3000:3000 -e GF_SECURITY_ADMIN_PASSWORD=admin \
  docker.io/grafana/grafana:11.3.0
curl -u admin:admin -X POST localhost:3000/api/datasources -H "Content-Type: application/json" \
  -d '{"name":"prometheus-demo","type":"prometheus","url":"http://host.containers.internal:9090","access":"proxy","isDefault":true}'
curl -u admin:admin -G 'localhost:3000/api/datasources/proxy/uid/<uid>/api/v1/query' \
  --data-urlencode 'query=up{job="observability-app"}'
```

Confirmed: Grafana successfully queried live data (`up{job="observability-app"} = 1`) through its
own datasource proxy — app → Prometheus → Grafana, all real, no mocking.

### closed-model.js vs open-model.js — the load-testing mistake, proven with real numbers

Both scripts hit `/bottleneck`, an endpoint with `Semaphore(3)` capacity and a fixed 200ms service
time (true throughput ceiling: ~15 req/s). Run with `k6 run <file>.js`.

| | Closed model (25 VUs) | Open model (constant 25 req/s) |
|---|---|---|
| Achieved throughput | 14.57 req/s | 14.59 req/s |
| p95 latency | **1.83s** | **4.04s** |
| Dropped iterations | n/a (concept doesn't exist) | **46 (3.27/s)** |

Both hit almost exactly the same achieved throughput — the server's real capacity ceiling. But the
**closed model silently throttled its own offered load** to match that ceiling (it can't do
anything else: each VU waits for a response before sending the next request), reporting a
comparatively tame p95 of 1.83s. The **open model kept firing requests at 25/s regardless**, which
is what real, external traffic actually does under overload — and revealed the true cost: p95
more than **doubled to 4.04s**, and **46 requests never even got sent** because k6 ran out of VUs
trying to keep up with the arrival rate against a queue that kept growing. A closed-model load
test of this exact server would have reported "looks fine" while genuinely hiding more than half
of the real overload behavior.

## How to use this progression

Run the k6 comparison yourself and watch the dropped-iteration count climb in real time under the
open model — it's the single clearest piece of evidence in this whole topic for why the load
testing model you choose isn't a neutral implementation detail.
