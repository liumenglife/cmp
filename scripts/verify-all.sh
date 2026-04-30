#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PHASE_NAMES=()
PHASE_DURATIONS=()
cleanup_done=0

format_duration() {
  local seconds="$1"
  printf '%02d:%02d' "$((seconds / 60))" "$((seconds % 60))"
}

print_summary() {
  printf '\n阶段耗时汇总\n'
  for i in "${!PHASE_NAMES[@]}"; do
    printf '  - %s: %s\n' "${PHASE_NAMES[$i]}" "$(format_duration "${PHASE_DURATIONS[$i]}")"
  done
}

record_phase() {
  local name="$1"
  local duration="$2"

  PHASE_NAMES+=("${name}")
  PHASE_DURATIONS+=("${duration}")
  if [[ -n "${PHASE_RECORD_FILE:-}" ]]; then
    printf '%s\t%s\n' "${name}" "${duration}" >> "${PHASE_RECORD_FILE}"
  fi
}

load_phase_records() {
  local record_file="$1"
  local name
  local duration

  [[ -f "${record_file}" ]] || return 0
  while IFS=$'\t' read -r name duration; do
    PHASE_NAMES+=("${name}")
    PHASE_DURATIONS+=("${duration}")
  done < "${record_file}"
}

run_phase() {
  local name="$1"
  shift
  local start
  local end
  local duration
  local status

  printf '\n>>> 开始：%s\n' "${name}"
  start="$(date +%s)"
  set +e
  "$@"
  status="$?"
  set -e
  end="$(date +%s)"
  duration="$((end - start))"
  record_phase "${name}" "${duration}"
  if (( status == 0 )); then
    printf '<<< 完成：%s，用时 %s\n' "${name}" "$(format_duration "${duration}")"
  else
    printf '<<< 失败：%s，用时 %s，退出码 %s\n' "${name}" "$(format_duration "${duration}")" "${status}" >&2
  fi

  return "${status}"
}

frontend_dependencies_current() {
  [[ -d "${ROOT_DIR}/frontend/node_modules" ]] && \
    [[ -f "${ROOT_DIR}/frontend/node_modules/.pnpm/lock.yaml" ]] && \
    cmp -s "${ROOT_DIR}/frontend/pnpm-lock.yaml" "${ROOT_DIR}/frontend/node_modules/.pnpm/lock.yaml" && \
    (cd "${ROOT_DIR}/frontend" && pnpm install --frozen-lockfile --prefer-offline)
}

ensure_frontend_dependencies() {
  if frontend_dependencies_current; then
    printf '前端依赖与 package.json / pnpm-lock.yaml 一致。\n'
    return 0
  fi

  cd "${ROOT_DIR}/frontend"
  pnpm install --frozen-lockfile --prefer-offline
}

backend_tests() {
  cd "${ROOT_DIR}/backend"
  mvn package
}

frontend_lint() {
  cd "${ROOT_DIR}/frontend"
  pnpm lint
}

frontend_vitest() {
  cd "${ROOT_DIR}/frontend"
  pnpm test
}

frontend_build() {
  cd "${ROOT_DIR}/frontend"
  pnpm build
}

backend_validation_batch() {
  run_phase "后端测试" backend_tests
}

frontend_validation_batch() {
  run_phase "前端依赖检查" ensure_frontend_dependencies
  run_phase "前端 lint" frontend_lint
  run_phase "前端 vitest" frontend_vitest
  run_phase "前端 build" frontend_build
}

run_validation_batches() {
  local tmp_dir
  local backend_status
  local frontend_status

  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/verify-all.XXXXXX")"

  (PHASE_RECORD_FILE="${tmp_dir}/backend.records"; backend_validation_batch) > "${tmp_dir}/backend.log" 2>&1 &
  local backend_pid="$!"
  (PHASE_RECORD_FILE="${tmp_dir}/frontend.records"; frontend_validation_batch) > "${tmp_dir}/frontend.log" 2>&1 &
  local frontend_pid="$!"

  set +e
  wait "${backend_pid}"
  backend_status="$?"
  wait "${frontend_pid}"
  frontend_status="$?"
  set -e

  printf '\n===== 后端验证输出 =====\n'
  cat "${tmp_dir}/backend.log"
  printf '\n===== 前端验证输出 =====\n'
  cat "${tmp_dir}/frontend.log"

  load_phase_records "${tmp_dir}/backend.records"
  load_phase_records "${tmp_dir}/frontend.records"
  rm -rf "${tmp_dir}"

  if (( backend_status != 0 )); then
    return "${backend_status}"
  fi
  return "${frontend_status}"
}

compose_config() {
  cd "${ROOT_DIR}"
  docker compose config --quiet
}

compose_build() {
  cd "${ROOT_DIR}"
  docker compose build
}

compose_up() {
  cd "${ROOT_DIR}"
  docker compose up -d --no-build
}

print_failure_diagnostics() {
  cd "${ROOT_DIR}"
  docker compose ps || true
  docker compose logs --tail=200 backend || true
  docker compose logs --tail=200 frontend || true
  docker compose logs --tail=120 mysql || true
  docker compose logs --tail=120 redis || true
}

smoke_test() {
  local deadline=$((SECONDS + 90))
  local backend_ready=0
  local frontend_ready=0

  while (( SECONDS < deadline )); do
    if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
      backend_ready=1
    fi

    if curl -fsS http://localhost:5173 | grep -q '<div id="root"></div>'; then
      frontend_ready=1
    fi

    if (( backend_ready == 1 && frontend_ready == 1 )); then
      return 0
    fi

    sleep 2
  done

  printf 'healthcheck / smoke test 超时：backend=%s frontend=%s\n' "${backend_ready}" "${frontend_ready}" >&2
  print_failure_diagnostics
  return 1
}

cleanup() {
  local status

  if (( cleanup_done == 1 )); then
    return 0
  fi
  cd "${ROOT_DIR}"
  docker compose down --remove-orphans
  status="$?"
  if (( status == 0 )); then
    cleanup_done=1
  else
    printf 'docker compose down --remove-orphans 清理失败，退出处理将再次尝试清理。\n' >&2
  fi
  return "${status}"
}

cleanup_phase() {
  run_phase "docker compose down / cleanup" cleanup
}

on_exit() {
  local status="$1"
  if (( status != 0 )); then
    print_failure_diagnostics
  fi
  if (( cleanup_done == 0 )); then
    cleanup_phase || true
  fi
  print_summary
  exit "${status}"
}

trap 'on_exit $?' EXIT

run_validation_batches
run_phase "docker compose config" compose_config
run_phase "镜像构建" compose_build
run_phase "docker compose up" compose_up
run_phase "healthcheck / smoke test" smoke_test
cleanup_phase
