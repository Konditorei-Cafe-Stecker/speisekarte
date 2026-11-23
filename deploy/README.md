# Running Café Stecker on Kubernetes

The website, the backend and the database run as separate parts that
Kubernetes starts, watches and copies as needed:

```text
guest ──▶ frontend (nginx, 2–4 copies) ──/api──▶ backend (Spring Boot, 2–3 copies) ──▶ PostgreSQL
            │ serves the React app                 │ staff sessions in PostgreSQL
            │ caches menu + recommendations 60 s   │ warm before ready
            │ each request to the least busy copy  │ health checks on port 8081
          CronJobs: context-update 06:15 (weather, Werder matches) · forecast-publish 06:45
          Job: menu-import (once)
```

| File | What it is |
|---|---|
| `kind-cluster.yaml` | Local cluster; publishes the website on <http://localhost:8088> |
| `k8s/postgres.yaml` | PostgreSQL 17 with a 2 GB disk; the app role `stecker_app` has no special rights |
| `k8s/backend.yaml` | Backend: 2 copies, a 3rd when their average CPU exceeds 70 % of the 0.25-CPU request (at most one new copy per minute); updates start a new copy before stopping an old one; node maintenance keeps at least 1 (PodDisruptionBudget) |
| `k8s/frontend.yaml` | nginx with the built website: 2–4 copies |
| `k8s/jobs.yaml` | Daily weather/match update, daily forecast, one-off menu import |
| `k8s/networkpolicy.yaml` | Only nginx may call the backend |
| `k8s/demo-data/` | Optional: the simulated order year for the planning page |
| `local-up.sh` / `local-down.sh` | Start / delete everything locally |
| `loadtest/guests.js` | k6 load test: 10,000 guests at the same time |
| `loadtest/run-in-cluster.sh` | Runs that test inside the cluster (the right way on a laptop) |

Images: `backend/Dockerfile`, `frontend/Dockerfile` (+ `frontend/nginx/`) and
`analysis/Dockerfile` (forecast and menu import, built from the repository
root). Deploy with `local-up.sh`, not `kubectl apply -k`: the manifests
name the images with the placeholder tag `dev`, which the script replaces
with the version it built.

## Start locally

Once: `brew install colima docker docker-buildx kind kubectl k6`

```bash
deploy/local-up.sh --demo-data
```

This starts Colima with 4 CPUs and 4 GB if it is not running yet (a
running Colima keeps its size: check with `colima list`; the script warns
below 4 CPUs / 4 GB, then `colima stop && deploy/local-up.sh`), creates the
cluster, builds the three images, creates the secrets, deploys, imports
the menu, and with `--demo-data` also generates the simulated year and
publishes a forecast. Then open <http://localhost:8088>. The first admin is
`chefin`; the temporary password is in a Kubernetes secret, never in git:

```bash
kubectl -n stecker get secret stecker-admin -o jsonpath='{.data.password}' | base64 -d; echo
```

Useful commands:

```bash
kubectl -n stecker get pods,hpa            # copies and autoscaling
kubectl -n stecker logs deploy/backend     # backend log (one copy)
kubectl -n stecker delete job weather-now --ignore-not-found; kubectl -n stecker create job weather-now --from=cronjob/context-update
deploy/local-down.sh                       # delete the cluster and its data
```

## Versions and rollback

Every deployment is a version you can name, see and go back to.

- **Version name from git.** `local-up.sh` names the images after
  `git describe --tags --always`: the commit (e.g. `6e67b74`), or with a
  release tag `v1.2.0-3-g6e67b74` (3 commits after v1.2.0). Builds with
  uncommitted or new, untracked files get `-dirty-<time>`, so they are
  never confused with a commit and always roll out. Characters Docker does
  not allow in a tag become `-` (`release/1.0` → `release-1.0`).
- **Visible in the running app.** The website shows its version at
  <http://localhost:8088/version.json>, every image carries it as a label
  (`org.opencontainers.image.version`), and the backend shows version and
  build time at `/actuator/info` on its health port, which only the
  cluster can reach:

```bash
kubectl -n stecker port-forward deploy/backend 18081:8081 >/dev/null & sleep 2; curl -s localhost:18081/actuator/info; echo; kill $!
```

- **History and rollback.** Kubernetes keeps the last 10 versions of each
  deployment, with the version as the change cause:

```bash
kubectl -n stecker rollout history deployment/backend     # which versions ran
kubectl -n stecker rollout undo deployment/backend        # back to the previous one
kubectl -n stecker rollout undo deployment/backend --to-revision=3
kubectl -n stecker get deploy -o 'custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image'
```

A rollback replaces the copies one by one like an update (a new copy is
ready before an old one stops). Tried locally: back to the previous image
and forward again; errors during it were not measured. kubectl warns that
the rollback does not update the "last-applied" note of `kubectl apply`;
that is expected here, because the next `local-up.sh` applies the complete
manifests of the checked-out commit again. Two things a rollback does not
undo:

- **Database migrations.** Flyway versions the database (V1–V5) and only
  goes forward. A new migration must therefore still work with the
  previous backend version (add columns and tables first, remove old ones
  in a later release), or the rollback would meet a database it does not
  understand.
- **CronJobs and the menu import** keep the version of the last
  `local-up.sh`, so the 06:15 context-update still starts the newer
  backend (and its Flyway migrations). Pause it with
  `kubectl -n stecker patch cronjob context-update -p '{"spec":{"suspend":true}}'`,
  or run `local-up.sh` on the old commit to move everything back.

To mark a release: `git tag v1.0.0 && git push origin v1.0.0`, then
`deploy/local-up.sh`; the images are then called `v1.0.0`.

Old image versions stay in the cluster, which is what makes a rollback
possible: the images were loaded with `kind load` and exist in no registry.
Do not run `crictl rmi --prune` to free space: it deletes every image no
pod uses right now, so rollbacks and the CronJobs fail until
`local-up.sh` runs again (or the images are loaded back with
`kind load docker-image --name stecker stecker-backend:<version> …`, while
they are still in the Mac's Docker). Remove old versions by name instead:

```bash
docker exec stecker-control-plane crictl rmi stecker-backend:<old> stecker-frontend:<old> stecker-tools:<old>
docker rmi stecker-backend:<old> stecker-frontend:<old> stecker-tools:<old>   # on the Mac
```

## What changed to allow several copies

In the backend:

- **Sessions in PostgreSQL** (Spring Session JDBC, migration V5). Before,
  a staff sign-in lived in one backend's memory; with two copies every
  second request would have looked signed out. The session cookie is now
  called `SESSION` (staff sign in again once).
- **Daily update as a CronJob.** Inside the app it would run once per copy.
  The copies run with `--stecker.context.daily-update.enabled=false`; the
  CronJob starts the backend with the `context-update-job` profile, which
  updates once, sends the "context updated" event and stops. Without
  Kubernetes nothing changes.
- **Daily follow-up work** (DailyDataUpdate: synthetic orders, forecast
  via Python) stays off in the cluster: the backend image has no Python
  and daily synthetic orders are only for local development. The
  `forecast-publish` CronJob publishes the forecast instead.
- **Health checks** for Kubernetes: `/actuator/health/liveness` and
  `/readiness` (only UP/DOWN, no details), plus `/actuator/info` with the
  version; all other Actuator endpoints are closed. In the cluster they
  run on their own port (8081) with their own threads, so a busy copy
  still answers Kubernetes; no service exposes that port.
- **Graceful shutdown**: a stopping copy keeps serving for 15 s while it
  disappears from DNS and nginx notices, then finishes running requests
  (up to 20 s). nginx tries another copy if one answers 503 while
  stopping (never for a POST, so no order is sent twice).
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

Around it:

- **No fixed number of copies** in the deployments: the autoscalers
  decide, so a deploy during a rush does not shrink the site.
- **nginx spreads single requests** over all ready copies (`least_conn`
  on the headless service `backend-pods`, looked up every 5 s), so a new
  copy gets traffic within seconds and a busy copy gets less.
- **Real guest address for the login limit.** nginx replaces any
  `X-Forwarded-For` header with the guest's address as it determined it,
  and the frontend service keeps the client's address
  (`externalTrafficPolicy: Local`). Only a proxy in the range
  `TRUSTED_PROXY_CIDR` (none by default) may report a guest's address and
  `https` to nginx. The network policy lets only nginx reach the backend,
  so nothing else in the cluster can send made-up addresses, and in local
  development (`local` profile) the backend ignores the header entirely.

Known limit: the login limiter counts per copy, so with n copies up to
n × 5 wrong passwords per username and 15 minutes get through. Still far
too few to guess a password; a shared limit would need the database or
Redis.

## Load test: 10,000 guests

```bash
deploy/loadtest/run-in-cluster.sh     # recommended on a laptop
k6 run deploy/loadtest/guests.js      # from the Mac, through the tunnel
```

k6 cannot run 10,000 browsers on a laptop, so it sends what 10,000
simultaneous guests would send: a 4-minute visit means 42 new visits per
second, each loading the page (index.html, its script, stylesheet and hero
image), the menu and today's recommendations; 30 % order, and waiting
guests check their order every 10 seconds. That is about 415 requests per
second for 3 minutes, after a 1-minute ramp. Before it starts, k6 creates
the ~1,500 orders that guests are waiting on at any moment.

Pass (k6 exits with an error otherwise): fewer than 1 % errors, the whole
load actually started (`dropped_iterations` = 0: every planned visit,
menu load, order and status check), and 95 % of pages, menu,
recommendations and status checks under 0.5 s, orders under 1 s.

On a laptop, the cluster is one 4-CPU virtual machine, so more copies do
not add CPU; the autoscaling only shows that it works. Real capacity grows
with the nodes of a cloud cluster. Run the test inside the cluster: from
the Mac, every request passes Colima's SSH tunnel and docker-proxy, which
under this load used more CPU than the café app (measured) and made the
whole VM unresponsive.

### Results on a MacBook (8 GB, Colima VM with 4 CPUs and 4 GB)

RESULTS_PLACEHOLDER

Orders from the test have the note "Lasttest (k6)". Remove them:

```bash
kubectl -n stecker exec statefulset/postgres -- psql -U stecker_app -d stecker -c \
  "DELETE FROM order_items WHERE order_id IN (SELECT id FROM customer_orders WHERE guest_note = 'Lasttest (k6)');
   DELETE FROM customer_orders WHERE guest_note = 'Lasttest (k6)';"
```

## Moving to a cloud cluster

The same manifests work on GKE, EKS or AKS with these changes:

1. **Images:** push them to a registry (e.g. GitHub Container Registry)
   and set `newName` and `newTag` (the version) for all three images in
   `k8s/kustomization.yaml`; for a private registry add
   `imagePullSecrets`. `local-up.sh` only works with kind.
2. **Secrets:** create them yourself:
   `kubectl -n stecker create secret generic stecker-db --from-literal=password=… --from-literal=postgres-password=…`
   and `stecker-admin` (keys `username`, `password`).
3. **Metrics:** the autoscalers need metrics-server (GKE and AKS have it;
   on EKS install it).
4. **Entry and HTTPS:** replace the NodePort of the frontend with an
   Ingress and an HTTPS certificate (cert-manager). Set
   `TRUSTED_PROXY_CIDR` in `k8s/frontend.yaml` to the Ingress
   controller's address range, so nginx takes the guest's address and
   `https` from it, and add `--server.servlet.session.cookie.secure=true`
   to the backend's arguments. (A `type: LoadBalancer` service with
   `externalTrafficPolicy: Local` keeps the guest's address without a
   trusted range.)
5. **Database:** for a managed PostgreSQL (backups, failover) instead of
   `k8s/postgres.yaml`, create the role `stecker_app` and the database
   `stecker` it owns (as the init script in `postgres.yaml` does), and
   point the name `postgres` at it with an `ExternalName` service. Each
   backend copy holds up to 10 connections: keep (maxReplicas + 1) × 10,
   plus a few for the jobs, below the database's `max_connections` when
   you raise `maxReplicas` with the number of nodes.
