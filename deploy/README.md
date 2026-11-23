# Running Café Stecker on Kubernetes

The website, the backend and the database run as separate parts that
Kubernetes starts, watches and copies as needed:

```text
guest ──▶ frontend (nginx, 2–4 copies) ──/api──▶ backend (Spring Boot, 2–3 copies) ──▶ PostgreSQL
            │ serves the React app                 │ staff sessions in PostgreSQL
            │ caches menu + recommendations 60 s   │ warm before ready, health checks
            │ each request to the least busy copy  │
          CronJobs: context-update 06:15 (weather, Werder matches) · forecast-publish 06:45
          Job: menu-import (once)
```

| File | What it is |
|---|---|
| `kind-cluster.yaml` | Local cluster; publishes the website on <http://localhost:8088> |
| `k8s/postgres.yaml` | PostgreSQL 17 with a 2 GB disk; the app role `stecker_app` has no special rights |
| `k8s/backend.yaml` | Backend: 2 copies, grows to 3 at 70 % CPU (one new copy per minute), never fewer than 1 during updates |
| `k8s/frontend.yaml` | nginx with the built website: 2–4 copies |
| `k8s/jobs.yaml` | Daily weather/match update, daily forecast, one-off menu import |
| `k8s/demo-data/` | Optional: the simulated order year for the planning page |
| `local-up.sh` / `local-down.sh` | Start / delete everything locally |
| `loadtest/guests.js` | k6 load test: 10,000 guests at the same time |
| `loadtest/run-in-cluster.sh` | Runs that test inside the cluster (the right way on a laptop) |

Images: `backend/Dockerfile`, `frontend/Dockerfile` (+ `frontend/nginx/`) and
`analysis/Dockerfile` (forecast and menu import, built from the repository
root).

## Start locally

Once: `brew install colima docker docker-buildx kind kubectl k6`

```bash
deploy/local-up.sh --demo-data
```

This starts Colima (Docker, 4 CPUs, 4 GB), creates the cluster, builds the
three images, creates the secrets, deploys, imports the menu, and with
`--demo-data` also generates the simulated year and publishes a forecast.
Then open <http://localhost:8088>. The first admin is `chefin`; the
temporary password is in a Kubernetes secret, never in git:

```bash
kubectl -n stecker get secret stecker-admin -o jsonpath='{.data.password}' | base64 -d; echo
```

Useful commands:

```bash
kubectl -n stecker get pods,hpa            # copies and autoscaling
kubectl -n stecker logs deploy/backend     # backend log (one copy)
kubectl -n stecker create job weather-now --from=cronjob/context-update
deploy/local-down.sh                       # delete the cluster and its data
```

## Versions and rollback

Every deployment is a version you can name, see and go back to.

- **Version name from git.** `local-up.sh` names the images after
  `git describe --tags --always --dirty`: the commit (e.g. `6e67b74`), or
  with a release tag `v1.2.0-3-g6e67b74` (3 commits after v1.2.0). Builds
  with uncommitted changes get `-dirty-<time>`, so they are never confused
  with a commit.
- **Visible in the running app.** The backend shows its version and build
  time at `/actuator/info` (inside the cluster; nginx does not forward
  it), the website at <http://localhost:8088/version.json>, and every image
  carries the version as a label (`org.opencontainers.image.version`).
- **History and rollback.** Kubernetes keeps the last 10 versions of each
  deployment, with the version as the change cause:

```bash
kubectl -n stecker rollout history deployment/backend     # which versions ran
kubectl -n stecker rollout undo deployment/backend        # back to the previous one
kubectl -n stecker rollout undo deployment/backend --to-revision=3
kubectl -n stecker get deploy -o 'custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image'
```

A rollback replaces the copies one by one, like an update, so the site
stays up (tried locally: back to the previous image and forward again).
kubectl warns that the rollback does not update the
"last-applied" note of `kubectl apply`; that is expected here, because the
next `local-up.sh` applies the complete manifests of the checked-out
commit again. Two things a rollback does not undo:

- **Database migrations.** Flyway versions the database (V1–V5) and only
  goes forward. A new migration must therefore still work with the
  previous backend version (add columns and tables first, remove old ones
  in a later release), or the rollback would meet a database it does not
  understand.
- **CronJobs and the menu import** keep the version of the last
  `local-up.sh`; run it again on the old commit to move everything back.

To mark a release: `git tag v1.0.0 && git push origin v1.0.0`, then
`deploy/local-up.sh`; the images are then called `v1.0.0`.

Old image versions stay in the cluster for rollbacks. To free disk space:
`docker exec stecker-control-plane crictl rmi --prune` (only removes
images no pod uses) and `docker image prune`.

## What changed in the backend to allow several copies

- **Sessions in PostgreSQL** (Spring Session JDBC, migration V5). Before,
  a staff sign-in lived in one backend's memory; with two copies every
  second request would have looked signed out. The session cookie is now
  called `SESSION` (staff sign in again once).
- **Daily update as a CronJob.** Inside the app it would run once per copy.
  The copies run with `--stecker.context.daily-update.enabled=false`; the
  CronJob starts the backend with the `context-update-job` profile, which
  updates once and stops. Without Kubernetes nothing changes.
- **Health checks** for Kubernetes: `/actuator/health/liveness` and
  `/readiness` (only UP/DOWN, no details), plus `/actuator/info` with the
  version; all other Actuator endpoints are closed.
- **Graceful shutdown**: running requests finish during updates and
  scale-down.
- **First admin**: if several copies start at once on an empty database,
  only one creates the account, the others carry on.
- **Warm before ready.** A new Java process is slow until it has compiled
  its hot code. Each copy first sends 500 rounds of guest requests to
  itself (menu, unknown order, empty order: nothing is written) and only
  then reports ready (`JitWarmup`).
- **JVM settings for containers** (`backend/Dockerfile`): the Parallel
  collector with a fixed heap of 50 % of the memory limit (the default
  Serial collector stopped a copy for up to 0.8 s while its heap grew),
  JVM threads sized for 2 CPUs, and Tomcat capped at 50 threads (with
  200, copies ran out of memory under load and were restarted).
- **Menu cache.** Browsers may reuse the menu for 30 s
  (`Cache-Control`), nginx keeps it and today's recommendations for 60 s
  whatever query string is added. A changed menu reaches guests within
  about 1.5 minutes.
- **Daily follow-up work** (DailyDataUpdate: synthetic orders, forecast
  via Python) stays off in the cluster: the backend image has no Python
  and daily synthetic orders are only for local development. The
  `forecast-publish` CronJob publishes the forecast instead; the
  `context-update` job still sends the "context updated" event.
- **Real guest address for the login limit.** nginx replaces any
  `X-Forwarded-For` header a client sends with the address it sees, and
  the frontend service keeps the client's address
  (`externalTrafficPolicy: Local`). Behind a cloud load balancer or an
  Ingress, configure it to pass the real client address the same way.

nginx spreads single requests over all ready copies (`least_conn` on the
headless service `backend-pods`, looked up every 5 s), so a new copy gets
traffic within seconds and a busy copy gets less.

Known limit: the login limiter counts per copy, so with n copies up to
n × 5 wrong passwords per username and 15 minutes get through. Still far too few to
guess a password; a shared limit would need the database or Redis.

## Load test: 10,000 guests

```bash
deploy/loadtest/run-in-cluster.sh     # recommended on a laptop
k6 run deploy/loadtest/guests.js      # from the Mac, through the tunnel
```

k6 cannot run 10,000 browsers on a laptop, so it sends what 10,000
simultaneous guests would send: a 4-minute visit means 42 new visits per
second (page + menu), 30 % order, and waiting guests check their order
every 10 seconds. That is about 320 requests per second for 3 minutes,
after a 1-minute ramp. Before it starts, k6 creates the ~1,500 orders
that guests are waiting on at any moment.

Pass (k6 exits with an error otherwise): fewer than 1 % errors, the whole
load actually sent (`dropped_iterations` = 0), and 95 % of pages, menu
and status checks under 0.5 s, orders under 1 s.

On a laptop, the cluster is one 4-CPU virtual machine, so more copies do
not add CPU; the autoscaling only shows that it works. Real capacity grows
with the nodes of a cloud cluster. Run the test inside the cluster: from
the Mac, every request passes Colima's SSH tunnel and docker-proxy, which
under this load used more CPU than the café app (measured) and made the
whole VM unresponsive.

### Results on a MacBook (8 GB, Colima VM with 4 CPUs and 4 GB)

Two runs of `run-in-cluster.sh` with everything above in place (23 Nov
2026), 10,000 guests, backend scaling from 2 to 3 copies:

| | Run A | Run B | Target |
|---|---|---|---|
| Requests | 81,866 | 81,276 | – |
| Failed requests | 0 % | 0 % | < 1 % |
| Page, p95 | 27 ms | 122 ms | < 500 ms |
| Menu, p95 | 30 ms | 168 ms | < 500 ms |
| Order status, p95 | 141 ms | **589 ms** | < 500 ms |
| Send order, p95 | 247 ms | 897 ms | < 1 s |
| Requests k6 could not send | **1,059 (1.3 %)** | **1,435 (1.8 %)** | 0 |
| Backend restarts | 0 | 0 | 0 |

So neither run passes every threshold: 10,000 simultaneous guests is the
limit of this laptop. The slow seconds come when the third copy starts
(its warm-up takes ~1.2 CPU) exactly as the load peaks, on a VM that also
runs Kubernetes itself and k6. In a cloud cluster the new copy lands on
its own CPUs, and a known rush (Freimarkt, a home match) can be met by
raising `minReplicas` beforehand.

How the setup got there (same test each time): the first version had
0 errors but slow orders (1.14 s) caused by cold JVMs, 0.8 s GC pauses
and uneven balancing; fixing those with a fixed large heap and 4 copies
filled the VM's memory (13 % errors); with copies capped at 3 they then
hit their own memory limit under load and were OOM-killed (3 % errors)
until Tomcat threads were capped and the heap fixed at 50 %. Tests from the Mac through the
Colima tunnel were not usable (see above).

Orders from the test have the note "Lasttest (k6)". Remove them:

```bash
kubectl -n stecker exec statefulset/postgres -- psql -U stecker_app -d stecker -c \
  "DELETE FROM order_items WHERE order_id IN (SELECT id FROM customer_orders WHERE guest_note = 'Lasttest (k6)');
   DELETE FROM customer_orders WHERE guest_note = 'Lasttest (k6)';"
```

## Moving to a cloud cluster

The same manifests work on GKE, EKS or AKS with three changes:

1. Push the images to a registry (e.g. GitHub Container Registry) and set
   the names in `k8s/kustomization.yaml` (`images:`).
2. Replace the NodePort of the frontend with an Ingress and an HTTPS
   certificate (cert-manager), and set
   `server.servlet.session.cookie.secure=true`.
3. Use a managed PostgreSQL (backups, failover) instead of
   `k8s/postgres.yaml`, and raise `maxReplicas` as the nodes allow.
