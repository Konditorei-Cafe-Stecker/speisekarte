#!/usr/bin/env bash
# Starts Café Stecker in a local Kubernetes cluster (kind in Colima).
#
#   deploy/local-up.sh              # cluster, images, database, website
#   deploy/local-up.sh --demo-data  # also the simulated order year + forecast
#
# Afterwards the website is on http://localhost:8088.
# Needs: colima, docker, kind, kubectl  (brew install colima docker kind kubectl)
set -euo pipefail
cd "$(dirname "$0")/.."

NS=stecker
CLUSTER=stecker
DEMO=false
[[ "${1:-}" == "--demo-data" ]] && DEMO=true

step() { printf '\n==> %s\n' "$*"; }

step "Docker (Colima)"
colima status >/dev/null 2>&1 || colima start --cpu 4 --memory 4 --disk 30 --vm-type vz

step "Kubernetes cluster '$CLUSTER'"
if ! kind get clusters | grep -qx "$CLUSTER"; then
  kind create cluster --config deploy/kind-cluster.yaml --wait 180s
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null
if ! kubectl -n kube-system get deployment metrics-server >/dev/null 2>&1; then
  # Needed by the autoscalers (CPU usage of the pods).
  kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.7.2/components.yaml
  kubectl -n kube-system patch deployment metrics-server --type=json \
    -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
fi

# Every build is named after its git version: the newest tag and commit,
# e.g. v1.2.0-3-g6e67b74, or just the commit while there is no tag.
# Uncommitted changes get "-dirty-<time>", so every local build is a new
# version that Kubernetes rolls out (and can roll back).
VERSION="$(git describe --tags --always --dirty)"
[[ "$VERSION" == *-dirty ]] && VERSION="$VERSION-$(date +%Y%m%d%H%M%S)"

step "Images, version $VERSION"
for part in backend frontend; do
  docker build --build-arg APP_VERSION="$VERSION" -t "stecker-$part:$VERSION" "$part"
done
docker build --build-arg APP_VERSION="$VERSION" -f analysis/Dockerfile -t "stecker-tools:$VERSION" .
kind load docker-image --name "$CLUSTER" \
  "stecker-backend:$VERSION" "stecker-frontend:$VERSION" "stecker-tools:$VERSION"

# The manifests name the images with the placeholder tag "dev"; deploy
# this version instead.
with_version() { sed -E "s#image: (stecker-(backend|frontend|tools)):dev\$#image: \1:${VERSION}#"; }

step "Secrets (created once, never stored in git)"
kubectl apply -f deploy/k8s/namespace.yaml >/dev/null
# stecker-db: "password" for the app role stecker_app, "postgres-password"
# for the database superuser (administration only).
if ! kubectl -n "$NS" get secret stecker-db >/dev/null 2>&1; then
  kubectl -n "$NS" create secret generic stecker-db \
    --from-literal=password="$(openssl rand -base64 24 | tr -d '/+=')" \
    --from-literal=postgres-password="$(openssl rand -base64 24 | tr -d '/+=')"
elif [[ -z "$(kubectl -n "$NS" get secret stecker-db -o jsonpath='{.data.postgres-password}')" ]]; then
  echo "The secret stecker-db is from an older version (no postgres-password)." >&2
  echo "Delete the cluster with deploy/local-down.sh and start again." >&2
  exit 1
fi
if ! kubectl -n "$NS" get secret stecker-admin >/dev/null 2>&1; then
  # The first admin's temporary password; it must be changed at first sign-in.
  kubectl -n "$NS" create secret generic stecker-admin \
    --from-literal=username="${STECKER_BOOTSTRAP_ADMIN_USERNAME:-chefin}" \
    --from-literal=password="${STECKER_BOOTSTRAP_ADMIN_PASSWORD:-$(openssl rand -base64 18 | tr -d '/+=')}"
fi

step "Deploy"
# A finished Job cannot be changed; remove it so it is created (and the
# idempotent menu import run) again.
kubectl -n "$NS" delete job menu-import --ignore-not-found >/dev/null
kubectl kustomize deploy/k8s | with_version | kubectl apply -f -
# Written into the rollout history: kubectl -n stecker rollout history deployment/backend
kubectl -n "$NS" annotate deployment/backend deployment/frontend --overwrite \
  kubernetes.io/change-cause="version $VERSION" >/dev/null
kubectl -n "$NS" rollout status statefulset/postgres --timeout=180s
kubectl -n "$NS" rollout status deployment/backend --timeout=300s
kubectl -n "$NS" rollout status deployment/frontend --timeout=120s
kubectl -n "$NS" wait --for=condition=complete job/menu-import --timeout=300s

if $DEMO; then
  step "Demo data: simulated order year, then the forecast"
  kubectl -n "$NS" delete job synthetic-history --ignore-not-found >/dev/null
  with_version < deploy/k8s/demo-data/synthetic-history.yaml | kubectl -n "$NS" apply -f -
  kubectl -n "$NS" wait --for=condition=complete job/synthetic-history --timeout=600s
  kubectl -n "$NS" delete job forecast-now --ignore-not-found >/dev/null
  kubectl -n "$NS" create job forecast-now --from=cronjob/forecast-publish
  kubectl -n "$NS" wait --for=condition=complete job/forecast-now --timeout=600s
fi

step "Ready"
kubectl -n "$NS" get pods
cat <<INFO

Version:      $VERSION   (website: http://localhost:8088/version.json)
Website:      http://localhost:8088
Staff login:  http://localhost:8088/personal   (user: $(kubectl -n "$NS" get secret stecker-admin -o jsonpath='{.data.username}' | base64 -d))
First admin password (temporary, change it at first sign-in):
  kubectl -n $NS get secret stecker-admin -o jsonpath='{.data.password}' | base64 -d; echo
Versions:         kubectl -n $NS rollout history deployment/backend
Roll back:        kubectl -n $NS rollout undo deployment/backend   (and deployment/frontend)
Stop everything:  deploy/local-down.sh
INFO
