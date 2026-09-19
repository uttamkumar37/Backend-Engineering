# Topic 8 — Docker, Kubernetes, CI/CD, Terraform, AWS

Assumes you can already write a `Dockerfile` and a basic GitHub Actions workflow. This is about
the JVM-specific gotchas containers introduce, the probe/resource-limit misconfigurations that
cause real production incidents, and the infrastructure-as-code discipline that prevents
"it worked when I ran it locally" deployments.

---

## 1. Docker — JVM-specific realities, not generic container advice

- **Multi-stage builds exist for image size and attack surface, not just tidiness**: a build
  stage with the full JDK/Maven/Gradle toolchain produces the artifact, and a separate final
  stage copies only the built JAR onto a minimal runtime base (a JRE-only or distroless image) —
  this can cut image size by hundreds of MB, which directly affects deployment speed (pulling a
  large image on every new pod/node) and reduces the vulnerability surface (a shipped build
  toolchain is attack surface with zero runtime value).
- **Layer caching is why `COPY pom.xml`/`build.gradle` before `COPY src` matters** — Docker
  caches each layer keyed by its inputs; copying dependency manifests and running dependency
  resolution *before* copying source code means source-only changes (the overwhelming majority of
  commits) don't invalidate the expensive dependency-download layer, cutting CI build time
  significantly. Getting the `COPY` order backwards (source first) silently defeats this and is a
  very common cause of "why does every CI build re-download every dependency."
- **The container-awareness JVM flag matters and has bitten real production systems.** Before
  JDK 10, the JVM read host-level `/proc/meminfo`/CPU count, completely ignoring cgroup
  limits — a JVM in a container capped at 512MB by Kubernetes would still see and size its default
  heap off the *host's* total memory, reliably leading to `OOMKilled` once real traffic arrived,
  because the JVM never knew it had less memory than it thought. JDK 10+ enables
  `-XX:+UseContainerSupport` by default, correctly reading cgroup limits — but **`-Xmx` set as an
  explicit absolute value that's close to or exceeds the container memory limit still causes
  `OOMKilled`**, because heap is only part of JVM memory footprint (metaspace, thread stacks,
  direct/native buffers, JIT code cache, GC bookkeeping all sit outside the heap). The safer
  approach is `-XX:MaxRAMPercentage` (e.g., 70–75%) rather than a fixed `-Xmx`, leaving explicit
  headroom for non-heap memory, and matching the container memory *limit* (not request) as the
  ceiling the JVM should reason about.
- **`-XX:ActiveProcessorCount` / container CPU limits interact with JVM sizing decisions you don't
  see directly** — the JVM sizes its default GC thread count and common fork-join pools (including
  the one virtual threads use as their carrier pool, Topic 1) based on the visible CPU count. A
  container CPU *limit* far below the *request*, or below the node's actual core count, can leave
  the JVM believing it has more parallelism available than its CPU quota actually allows,
  contributing to the CPU throttling problem described in Section 2.

---

## 2. Kubernetes — probes, resource limits, and graceful shutdown are where incidents live

- **Liveness vs readiness probes are not interchangeable, and confusing them causes real
  outages.** A **liveness** probe failing causes Kubernetes to **kill and restart** the container
  — appropriate only for "this process is truly deadlocked/unrecoverable," never for "this
  instance is temporarily slow." A **readiness** probe failing removes the pod from the Service's
  load-balancing endpoints **without restarting it** — appropriate for "temporarily can't serve
  traffic" (e.g., still warming up, a downstream dependency briefly unavailable, or——directly
  relevant to Topic 5——a Kafka consumer mid-rebalance). **Using a liveness probe that reflects
  downstream dependency health is a classic self-inflicted cascading failure**: if the liveness
  check pings a database and the database is briefly slow, Kubernetes restarts every pod
  simultaneously, which does nothing to fix the database and instead adds a thundering herd of
  reconnecting/rebalancing pods on top of it — the fix is to make liveness reflect only "is this
  process's own event loop/thread pool alive," and let readiness (or the circuit breakers from
  Topic 4) handle downstream degradation.
- **Resource `requests` and `limits` serve different purposes and getting them backwards causes
  different failure modes.** `requests` are what the scheduler uses to place a pod on a node with
  enough *reserved* capacity — set too low, and too many pods get crammed onto a node, causing
  contention the scheduler never accounted for. `limits` are enforced hard ceilings — exceeding
  the **memory** limit gets the container `OOMKilled` immediately (no soft warning), while
  exceeding the **CPU** limit doesn't kill the process but **throttles** it via the CFS quota
  mechanism, silently adding latency (the process is paused for the remainder of each scheduling
  period once its quota is used) — this is invisible in application logs and shows up only as
  unexplained tail-latency spikes correlated with CPU throttling metrics (`container_cpu_cfs_throttled_seconds_total`
  in Prometheus/cAdvisor), a very common "why is p99 latency bad despite low average CPU usage"
  root cause that's easy to miss without specifically looking at throttling metrics (Topic 9).
- **Graceful shutdown requires explicit handling, not just relying on Kubernetes.** On pod
  termination, Kubernetes sends `SIGTERM`, waits up to `terminationGracePeriodSeconds` (default
  30s), then sends `SIGKILL`. Spring Boot's graceful shutdown
  (`server.shutdown=graceful`) stops accepting new requests and waits for in-flight ones to
  complete within `spring.lifecycle.timeout-per-shutdown-phase` — but this must be coordinated
  with the actual grace period and with the **readiness probe flipping to not-ready immediately
  on `SIGTERM`** (so the Service stops routing new traffic to a pod that's shutting down) *before*
  in-flight requests finish, not after — getting this ordering wrong causes dropped requests
  during every rolling deployment even though "graceful shutdown" is technically enabled. For a
  Kafka consumer pod specifically, an insufficient grace period during rolling deploys can kill
  the process mid-processing before it commits an offset, causing exactly the kind of reprocessing/
  rebalance-under-load behavior discussed in Topic 5 — grace periods for consumer workloads need
  to account for realistic in-flight batch processing time, not a generic default.
- **HPA (Horizontal Pod Autoscaler) on plain CPU utilization is a blunt instrument for I/O-bound
  services** — a service waiting on a slow downstream dependency or a database can have low CPU
  usage while still being saturated (all its threads/connections blocked on I/O, not doing CPU
  work), so CPU-based HPA under-scales exactly when it's needed most. Scaling on custom metrics
  (request queue depth, in-flight request count, consumer lag for a Kafka-consuming service) is
  usually a more accurate signal for backend services than CPU alone.
- **PodDisruptionBudgets (PDBs)** guard against voluntary disruptions (node drains, cluster
  upgrades) taking down too many replicas of a service at once — without one, a node drain during
  a cluster upgrade can legally evict every replica of a service simultaneously if the scheduler
  happened to place them on the same node/nodes being drained together, causing an availability
  gap that looks like a bug but is actually a missing safety configuration.

---

## 3. CI/CD (GitHub Actions) — build once, promote, don't rebuild per environment

- **"Build once, promote through environments" is the core discipline that CI/CD pipelines
  frequently violate without realizing it.** If each environment (dev → staging → prod) triggers
  a fresh build/compile of the same commit, you're no longer guaranteeing that what was tested in
  staging is bit-for-bit what runs in production — a different dependency resolution moment
  (a floating version range, a registry serving a different transitive dependency version between
  builds), a different build-time environment variable baked in, or a flaky compiler/test run can
  all make the staging and production artifacts subtly different despite "the same code." The
  correct pattern is to build and produce **one immutable artifact/image per commit**, run it
  through progressively more realistic test stages, and **promote the same artifact** (retagging
  or referencing the same image digest) to each environment rather than rebuilding.
- **Dependency/layer caching in Actions** (`actions/cache`, or Docker layer caching via
  `docker/build-push-action` with a registry or GitHub Actions cache backend) is what keeps build
  times from scaling linearly with a growing dependency tree — the same principle as Section 1's
  Docker layer ordering, applied at the CI level.
- **Secrets belong in GitHub Actions encrypted secrets (or an external secrets manager referenced
  at deploy time), never in workflow YAML or committed config** — and secrets used in a workflow
  triggered by `pull_request` from a fork are deliberately withheld by GitHub by default,
  specifically to prevent a malicious PR from exfiltrating secrets by modifying workflow code —
  understanding *why* this restriction exists (rather than working around it by switching to a
  less safe trigger) matters when a fork-based contribution workflow "mysteriously" can't access
  secrets.
- **Deployment should be automated but gated, not merely automated.** A pipeline that
  auto-deploys straight to 100% of production on every merge to main has no blast-radius control.
  **Progressive delivery** — canary (route a small percentage of traffic to the new version,
  watch error rate/latency, then ramp up) or blue-green (deploy the new version fully alongside
  the old, switch traffic over, keep the old version ready for instant rollback) — turns a bad
  deploy from a full-production incident into a contained, quickly-reverted one. This connects
  directly to Topic 9: canary analysis is only as good as the metrics it watches, and without
  proper observability a canary stage is just a delay, not a safety net.

---

## 4. Terraform — state, drift, and blast radius

- **Remote state with locking is not optional for any team beyond one person.** Local
  (`terraform.tfstate` on disk) state means two engineers running `terraform apply` concurrently
  can corrupt state or apply conflicting changes with no coordination. Remote state (S3 backend
  with DynamoDB state locking, or Terraform Cloud) makes state a shared, lockable resource — this
  is infrastructure hygiene, not a nice-to-have, and its absence is one of the most common causes
  of "someone's apply silently undid someone else's change" incidents in teams adopting Terraform
  without this from day one.
- **State drift** — the real infrastructure diverging from what's recorded in state, usually from
  a manual console change ("I just quickly fixed it in the AWS console") — is dangerous because
  the *next* `terraform apply` will "correct" the drift by reverting the manual change, often
  without anyone connecting the resulting incident to the Terraform run that caused it. The
  discipline this demands is: no manual infrastructure changes outside Terraform, ever, in any
  environment Terraform manages, full stop — and periodic `terraform plan` (or drift-detection
  tooling) to catch drift proactively rather than discovering it via an unexpected apply.
- **`terraform plan` before `apply` is the actual safety mechanism**, and its value is
  proportional to someone actually reading it carefully — a plan showing an unexpected "destroy
  and recreate" for a stateful resource (a database, citing an immutable attribute change like
  engine version requiring replacement) is exactly the kind of blast-radius signal that should
  block an apply and trigger a design conversation, not be scrolled past because "the plan output
  is always long."
- **Modules exist for reuse and consistency, not just DRY-ing YAML-adjacent config** — a shared
  module for "how we create an RDS instance" encodes the org's actual operational standards
  (backup retention, encryption at rest, parameter group settings, monitoring) in one reviewable
  place, so every team provisioning a database inherits those standards by construction instead of
  by every team remembering to configure them correctly independently.
- **Workspaces vs. separate state files per environment**: Terraform workspaces share the same
  configuration and backend but isolate state per workspace — convenient for near-identical
  environments, but a common trap is using workspaces for environments that actually need
  materially different configuration (different instance sizes, different account/region), which
  pushes conditional logic into the Terraform code itself. Many teams find fully separate state
  (and often separate directories/configuration) per environment easier to reason about and audit,
  accepting some duplication in exchange for environments that can't accidentally leak
  configuration or state into each other.

---

## 5. AWS — the services a backend engineer actually touches, and the decisions that matter

- **ECS vs EKS**: ECS is AWS's own simpler container orchestrator (task definitions, services,
  tight native integration with ALB/CloudWatch/IAM) — lower operational overhead, less
  flexibility. EKS is managed Kubernetes — worth the added complexity specifically when you need
  Kubernetes-ecosystem tooling (Helm charts, operators, a specific ingress controller, multi-cloud
  portability) or already have Kubernetes expertise in-house; choosing EKS "because Kubernetes is
  the industry standard" without that need just imports the probe/resource-limit complexity from
  Section 2 for no corresponding benefit.
- **RDS (Postgres) Multi-AZ** provides synchronous standby replication and automatic failover on
  primary failure — but failover still takes on the order of 60–120 seconds and involves a DNS
  endpoint switch, so application-side connection handling (retry with backoff on connection
  failure, not treating a failover as a fatal unrecoverable error) matters for actually surviving
  it gracefully rather than just having it available.
- **ElastiCache (Redis)** in cluster mode shards data across nodes for horizontal scale — but this
  reintroduces the same hot-key/shard-key reasoning from Topic 3's MongoDB discussion: a
  poorly-distributed key pattern creates a hot shard regardless of the overall cluster's capacity.
- **MSK (managed Kafka)** removes broker operational burden but not the partition-count,
  consumer-group, and retry-topic design decisions from Topic 5 — it's a hosting decision, not a
  substitute for correct application-level Kafka design.
- **IAM roles over long-lived access keys, always** — an EC2 instance profile, an ECS task role,
  or (for EKS) **IRSA (IAM Roles for Service Accounts)** lets a workload assume temporary,
  automatically-rotated credentials scoped to exactly what it needs, instead of a static access
  key/secret pair baked into an environment variable or config file — a static key is a permanent
  credential that, if leaked (committed to a repo, logged accidentally), remains valid until
  someone notices and rotates it manually; this is a common real breach vector and one of the more
  consequential AWS security defaults to actually enforce (e.g., via SCPs blocking `iam:CreateAccessKey`
  for most roles) rather than leave as "best practice" nobody enforces.
- **Security groups vs. NACLs**: security groups are stateful (a response to an allowed inbound
  request is automatically allowed back out) and attached to resources (instances, RDS, Lambda);
  NACLs are stateless (return traffic must be explicitly allowed) and attached to subnets. Most
  day-to-day access control for a backend service is security-group-based; NACLs are a coarser,
  subnet-wide defense-in-depth layer, not the primary tool, and forgetting NACL statelessness
  (allowing inbound but forgetting the ephemeral-port outbound return rule) is a classic
  "connection times out for no visible reason" networking bug.
- **Secrets Manager/Parameter Store (SecureString) over plain environment variables for real
  secrets** — env vars are visible to anything that can inspect the process/container
  (`docker inspect`, a container escape, a debugging endpoint that dumps env) and aren't rotated
  automatically; Secrets Manager supports automatic rotation and fine-grained IAM-based access
  control per secret, which is the actual reason to use it over "just put it in an env var," not
  just convention.

---

## Interview-depth Q&A

1. A Java service is repeatedly `OOMKilled` in Kubernetes despite `-Xmx` being set comfortably
   below the container memory limit. What's the most likely explanation, and how do you fix it?
2. Explain precisely why using a downstream database health check as a Kubernetes liveness probe
   can turn a brief database slowdown into a full-service outage.
3. A service shows low average CPU usage in dashboards but has unexplained p99 latency spikes.
   What metric would you check first, and why might the average be misleading here?
4. Why does "rebuild the artifact for each environment in the pipeline" undermine the guarantee
   CI/CD is supposed to provide, even if every build uses the exact same source commit?
5. A teammate manually changed a security group rule in the AWS console to unblock an urgent
   issue. What's the actual risk the next `terraform apply` introduces, and how do you prevent it
   going forward?
6. Walk through why a static IAM access key baked into an application's environment variables is a
   materially worse security posture than an IAM role/IRSA, beyond "it's a secret that could leak."

## Proof of learning
_Write one paragraph on a container OOM/throttling incident, a bad deploy, or an infrastructure
drift issue you've experienced or can imagine — which specific practice here (container-aware
heap sizing, readiness/liveness separation, remote state locking) would have prevented it?_
