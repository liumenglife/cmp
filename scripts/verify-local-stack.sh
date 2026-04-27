#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

cleanup() {
  docker compose down
}

print_failure_diagnostics() {
  docker compose ps
  docker compose logs backend
  docker compose logs frontend
}

trap cleanup EXIT

docker compose config --quiet
docker compose build
docker compose up -d

for attempt in {1..30}; do
  if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' && \
    curl -fsS http://localhost:5173 | grep -q '<div id="root"></div>'; then
    exit 0
  fi
  sleep 2
done

print_failure_diagnostics
exit 1
