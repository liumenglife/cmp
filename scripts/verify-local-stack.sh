#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

cleanup() {
  docker compose down --remove-orphans
}

print_failure_diagnostics() {
  docker compose ps || true
  docker compose logs --tail=200 backend || true
  docker compose logs --tail=200 frontend || true
  docker compose logs --tail=120 mysql || true
  docker compose logs --tail=120 redis || true
}

ensure_backend_artifact() {
  if [[ -f "${ROOT_DIR}/backend/target/cmp-backend-0.1.0-SNAPSHOT.jar" ]]; then
    return 0
  fi

  cd "${ROOT_DIR}/backend"
  mvn package
  cd "${ROOT_DIR}"
}

ensure_frontend_dist() {
  if [[ -f "${ROOT_DIR}/frontend/dist/index.html" ]]; then
    return 0
  fi

  cd "${ROOT_DIR}/frontend"
  if [[ -d node_modules ]] && [[ -f node_modules/.pnpm/lock.yaml ]] && cmp -s pnpm-lock.yaml node_modules/.pnpm/lock.yaml && pnpm install --frozen-lockfile --prefer-offline; then
    printf '前端依赖与 package.json / pnpm-lock.yaml 一致。\n'
  else
    pnpm install --frozen-lockfile --prefer-offline
  fi
  pnpm build
  cd "${ROOT_DIR}"
}

trap cleanup EXIT

ensure_backend_artifact
ensure_frontend_dist
docker compose config --quiet
docker compose build
docker compose up -d --no-build

for attempt in {1..30}; do
  if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' && \
    curl -fsS http://localhost:5173 | grep -q '<div id="root"></div>'; then
    exit 0
  fi
  sleep 2
done

print_failure_diagnostics
exit 1
