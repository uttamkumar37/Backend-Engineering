#!/bin/bash
# Reproduces, live, the concept doc's central claim: an explicit -Xmx bigger than the container's
# cgroup memory limit gets the WHOLE CONTAINER killed by the kernel OOM killer (SIGKILL, exit 137)
# the moment real usage crosses the limit - -XX:MaxRAMPercentage instead keeps the JVM inside the
# limit and fails at the Java level (survivable, loggable) instead.
#
# Requires: the image built in ../01-beginner/app (podman build -t container-demo-app:latest .)
set -euo pipefail

echo "=== Misconfigured: explicit -Xmx200m inside a 128MB container limit ==="
podman rm -f oom-demo >/dev/null 2>&1 || true
podman run -d --name oom-demo --memory=128m -p 8081:8080 \
  --entrypoint '["java","-Xmx200m","-jar","app.jar"]' container-demo-app:latest >/dev/null
sleep 2
podman logs oom-demo

echo "--- allocating 20MB at a time until something gives ---"
for i in $(seq 1 8); do
  status=$(curl -s -o /dev/null -w "%{http_code}" "localhost:8081/allocate?mb=20" || true)
  oom=$(podman inspect oom-demo --format '{{.State.OOMKilled}}' 2>/dev/null || echo "container gone")
  echo "  request $i -> HTTP $status, OOMKilled=$oom"
done
podman inspect oom-demo --format 'RESULT: ExitCode={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}}' 2>&1
podman rm -f oom-demo >/dev/null 2>&1 || true

echo
echo "=== Fixed: -XX:MaxRAMPercentage=70 inside the SAME 128MB container limit ==="
podman rm -f fixed-demo >/dev/null 2>&1 || true
podman run -d --name fixed-demo --memory=128m -p 8082:8080 \
  --entrypoint '["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]' container-demo-app:latest >/dev/null
sleep 2
podman logs fixed-demo

echo "--- same allocation pressure ---"
for i in $(seq 1 8); do
  status=$(curl -s -o /dev/null -w "%{http_code}" "localhost:8082/allocate?mb=20" || true)
  oom=$(podman inspect fixed-demo --format '{{.State.OOMKilled}}' 2>/dev/null || echo "container gone")
  echo "  request $i -> HTTP $status, OOMKilled=$oom"
done
echo "--- proving the container itself is still alive and serving other requests ---"
curl -s -w "\nhealth check: HTTP %{http_code}\n" localhost:8082/health/live
podman rm -f fixed-demo >/dev/null 2>&1 || true
