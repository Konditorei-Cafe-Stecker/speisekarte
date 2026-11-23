#!/usr/bin/env bash
# Runs the load test inside the cluster, next to the website.
#
#   deploy/loadtest/run-in-cluster.sh                       # 10,000 guests
#   GUESTS=5000 RAMP=20s HOLD=1m deploy/loadtest/run-in-cluster.sh
#
# Why not from the Mac? On a laptop, requests to http://localhost:8088
# travel through Colima's SSH tunnel and docker-proxy into the VM. Under
# load those used more CPU than the café app itself (measured: sshd and
# docker-proxy up to 47 % each, the VM fully busy), so the test measured
# the tunnel. Inside the cluster, k6 talks to the frontend service like a
# cloud load balancer would. k6 still shares the VM's CPUs with the app.
set -euo pipefail
cd "$(dirname "$0")"
NS=stecker
K6_VERSION=2.4.0
K6_IMAGE=stecker-k6:$K6_VERSION

# kind cannot load the multi-platform image from Docker Hub directly
# ("content digest not found"); a one-line local build makes it a plain
# single-platform image, like our own.
echo "FROM grafana/k6:$K6_VERSION" | docker build -q -t "$K6_IMAGE" - >/dev/null
kind load docker-image --name stecker "$K6_IMAGE" >/dev/null
# Remove the previous run and its pod completely, so nothing below picks up
# the old pod.
kubectl -n "$NS" delete job loadtest --ignore-not-found --cascade=foreground --wait >/dev/null
kubectl -n "$NS" create configmap loadtest-script --from-file=guests.js \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

kubectl -n "$NS" apply -f - <<JOB >/dev/null
apiVersion: batch/v1
kind: Job
metadata:
  name: loadtest
spec:
  backoffLimit: 0                 # a failed test is a result, not a reason to retry
  ttlSecondsAfterFinished: 3600
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: k6
          image: $K6_IMAGE
          args: ["run", "--quiet", "/scripts/guests.js"]
          env:
            - {name: BASE_URL, value: "http://frontend.$NS.svc.cluster.local"}
            - {name: GUESTS, value: "${GUESTS:-10000}"}
            - {name: RAMP, value: "${RAMP:-1m}"}
            - {name: HOLD, value: "${HOLD:-3m}"}
          resources:
            requests: {cpu: 500m, memory: 256Mi}
            limits: {memory: 1Gi}
          volumeMounts:
            - {name: script, mountPath: /scripts}
      volumes:
        - name: script
          configMap: {name: loadtest-script}
JOB

# The job creates its pod a moment later. Wait until k6 runs or has already
# ended (it ends within a second when, for example, the website is down;
# then there is no "ready" state to wait for, only its log to show).
phase=""
for _ in $(seq 1 120); do
  phase="$(kubectl -n "$NS" get pod -l job-name=loadtest \
    -o jsonpath='{.items[0].status.phase}' 2>/dev/null || true)"
  [[ "$phase" =~ ^(Running|Succeeded|Failed)$ ]] && break
  sleep 2
done
[[ "$phase" =~ ^(Running|Succeeded|Failed)$ ]] \
  || { echo "The load test pod did not start (phase: ${phase:-none})." >&2; exit 1; }
kubectl -n "$NS" logs -f job/loadtest

# k6 exits with an error when a threshold fails; the job then fails too.
for _ in $(seq 1 30); do
  succeeded="$(kubectl -n "$NS" get job loadtest -o jsonpath='{.status.succeeded}')"
  failed="$(kubectl -n "$NS" get job loadtest -o jsonpath='{.status.failed}')"
  [[ -n "$succeeded$failed" ]] && break
  sleep 2
done
if [[ "$succeeded" == 1 ]]; then
  echo "Load test passed."
else
  echo "Load test FAILED (see above)."
  exit 1
fi
