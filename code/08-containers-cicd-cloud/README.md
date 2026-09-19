# Code Progression — Topic 8: Docker, Kubernetes, CI/CD, Terraform, AWS

Companion code for [topics/08-containers-cicd-cloud.md](../../topics/08-containers-cicd-cloud.md).
Real container builds, real cgroup memory/CPU limits, real Terraform state — run via **Podman**
(a Docker-compatible engine, no Docker Desktop needed) plus a real Terraform install. No AWS
account was used or needed; the Terraform demo uses the `local` provider specifically so the
init/plan/apply/state/drift workflow is real without needing cloud credentials.

## Prerequisites

```bash
podman machine start                 # starts the container VM
terraform -version                   # confirms terraform (hashicorp/tap/terraform) is installed
```

## Beginner (`01-beginner/app/`)

A dependency-free Java app (plain `HttpServer`, no Spring Boot) with a multi-stage `Dockerfile`.

```bash
cd 01-beginner/app
podman build -t container-demo-app:latest .
podman run -d --name demo -p 8080:8080 container-demo-app:latest
curl localhost:8080/health/live      # 200 immediately
curl localhost:8080/health/ready     # 503 until /ready is called
curl localhost:8080/ready && curl localhost:8080/health/ready   # now 200
podman rm -f demo
```

Confirmed live: **JVM max heap defaults to ~1976 MB with no container memory limit** — the JVM
correctly reads the podman VM's available memory via container-aware ergonomics
(`-XX:+UseContainerSupport`, default since JDK 10).

## Intermediate (`02-intermediate/`)

### memory-limit-demo.sh — the concept doc's central JVM-in-containers claim, proven live

```bash
bash memory-limit-demo.sh
```

Confirmed, exactly as run in this session:
- **Misconfigured** (`-Xmx200m` inside a `--memory=128m` container): JVM reports a 193 MB max
  heap — already bigger than the container's limit. Gradually allocating real memory, the
  container was **killed by the kernel OOM killer** the moment RSS crossed 128 MB:
  `ExitCode=137 OOMKilled=true`. Every subsequent request got `HTTP 000` — the whole container
  was gone.
- **Fixed** (`-XX:MaxRAMPercentage=70` inside the same `--memory=128m` limit): JVM reports a
  61 MB max heap, safely inside the limit. Under the same allocation pressure, individual
  requests failed once the *Java heap* was exhausted, but **the container itself stayed alive**
  (`OOMKilled=false`, `status=running`) and kept serving `/health/live` — a contained, survivable
  failure instead of a kernel SIGKILL.

### pod.yaml — readiness vs. liveness, and a real surprise

```bash
podman kube play pod.yaml
curl localhost:8083/health/ready   # 503 before /ready is called
curl localhost:8083/ready
curl localhost:8083/health/ready   # 200 after
podman kube down pod.yaml
```

Confirmed live: the raw HTTP-level readiness distinction (503 → 200) worked as expected. But
`podman inspect ... RestartCount` showed **2 restarts** during this run, even though
`/health/live` returned 200 the entire time. `podman kube play` is a single-node dev tool, not a
real kubelet + kube-proxy — it doesn't implement true Service-based endpoint removal for a failing
*readiness* probe, and in this version appears to fold a failing readiness probe into the same
restart behavior a failing *liveness* probe would cause. This is, unintentionally, a very concrete
illustration of the concept doc's exact warning: **conflating readiness and liveness causes
unwanted restarts** — here it happened because of a tooling gap, in real Kubernetes it happens
because of a config mistake, but the observed failure mode is the same.

## Advanced (`03-advanced/`)

### CPU throttling (`cpu-work` endpoint)

```bash
podman run -d --name unthrottled -p 8084:8080 container-demo-app:latest
curl localhost:8084/cpu-work        # confirmed: 213 ms across 6 visible cores
podman run -d --name throttled --cpus=0.25 -p 8085:8080 container-demo-app:latest
curl localhost:8085/cpu-work        # confirmed: 909 ms, 1 visible core - ~4.3x slower
podman rm -f unthrottled throttled
```

The same unit of CPU-bound work took **~4.3x longer** under a `--cpus=0.25` quota — exactly the
CFS-throttling latency effect the concept doc describes, and exactly why CPU throttling is
invisible in a plain "average CPU usage" dashboard (Topic 9 covers the matching metric,
`container_cpu_cfs_throttled_seconds_total`, in depth).

### terraform-demo/ — a real init/plan/apply/drift loop, no cloud account needed

```bash
cd terraform-demo
terraform init
terraform plan     # shows: 1 to add
terraform apply -auto-approve
cat generated/app-config.json   # {"environment":"staging","replica_count":2}

# simulate "someone manually fixed something in the console"
echo '{"environment":"MANUALLY-HACKED-IN-CONSOLE","replica_count":999}' > generated/app-config.json
terraform plan      # confirmed: detects drift, wants to recreate
terraform apply -auto-approve
cat generated/app-config.json   # confirmed: back to {"environment":"staging","replica_count":2}
                                 # - the manual "fix" is silently gone, exactly as the concept
                                 # doc warns: the next apply reverts an out-of-band change
terraform destroy -auto-approve
```

### github-actions/build-and-promote.yml

A build-once-promote workflow (validated as syntactically correct YAML) implementing the
concept doc's Section 3: one image built and tagged by commit SHA, the *same* image digest
deployed to staging and then production — no rebuild step in either deploy job. Not run here
(no GitHub Actions runner in this environment); read it alongside the concept doc's discussion of
why rebuilding per environment breaks the "what was tested is what's deployed" guarantee.

## How to use this progression

Run `memory-limit-demo.sh` and the CPU throttling comparison yourself — the OOMKilled and
throttling numbers are far more convincing seen live than described in prose, and the
`podman kube play` restart-count surprise is worth reproducing to see how easily the
readiness/liveness distinction breaks down even in tooling built to support it.
