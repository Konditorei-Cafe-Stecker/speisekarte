#!/usr/bin/env bash
# Deletes the local cluster (including its database) and stops Colima.
set -euo pipefail
kind delete cluster --name stecker
colima stop
