#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

assert_contains() {
  local file="$1"
  local expected="$2"

  if ! grep -Fq -- "${expected}" "${file}"; then
    printf '缺少预期内容：%s -> %s\n' "${file}" "${expected}" >&2
    return 1
  fi
}

assert_not_contains_regex() {
  local file="$1"
  local unexpected="$2"

  if grep -Eq -- "${unexpected}" "${file}"; then
    printf '存在不应出现的内容：%s -> %s\n' "${file}" "${unexpected}" >&2
    return 1
  fi
}

assert_log_contains() {
  local log_file="$1"
  local expected="$2"

  if ! grep -Fq -- "${expected}" "${log_file}"; then
    printf '命令日志缺少预期调用：%s\n' "${expected}" >&2
    printf '当前命令日志：\n' >&2
    cat "${log_file}" >&2
    return 1
  fi
}

assert_log_count_at_least() {
  local log_file="$1"
  local expected="$2"
  local minimum="$3"
  local actual

  actual="$(grep -F -- "${expected}" "${log_file}" | wc -l | tr -d ' ')"
  if (( actual < minimum )); then
    printf '命令日志调用次数不足：%s，期望至少 %s 次，实际 %s 次\n' "${expected}" "${minimum}" "${actual}" >&2
    printf '当前命令日志：\n' >&2
    cat "${log_file}" >&2
    return 1
  fi
}

write_stub_commands() {
  local bin_dir="$1"
  local command_log="$2"

  mkdir -p "${bin_dir}"

  cat > "${bin_dir}/mvn" <<'STUB'
#!/usr/bin/env bash
printf 'mvn %s\n' "$*" >> "${VERIFY_ALL_STUB_LOG}"
exit 0
STUB

  cat > "${bin_dir}/pnpm" <<'STUB'
#!/usr/bin/env bash
printf 'pnpm %s\n' "$*" >> "${VERIFY_ALL_STUB_LOG}"
exit 0
STUB

  cat > "${bin_dir}/docker" <<'STUB'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "${VERIFY_ALL_STUB_LOG}"
  if [[ "$*" == "compose down --remove-orphans" && -f "${VERIFY_ALL_STUB_LOG}.fail-down-once" ]]; then
  count_file="${VERIFY_ALL_STUB_LOG}.down-count"
  count=0
  [[ -f "${count_file}" ]] && count="$(cat "${count_file}")"
  count="$((count + 1))"
  printf '%s' "${count}" > "${count_file}"
  if (( count == 1 )); then
    exit 17
  fi
fi
exit 0
STUB

  cat > "${bin_dir}/curl" <<'STUB'
#!/usr/bin/env bash
printf 'curl %s\n' "$*" >> "${VERIFY_ALL_STUB_LOG}"
case "$*" in
  *localhost:8080*) printf '{"status":"UP"}\n' ;;
  *localhost:5173*) printf '<div id="root"></div>\n' ;;
  *) printf 'stub curl response\n' ;;
esac
exit 0
STUB

  chmod +x "${bin_dir}/mvn" "${bin_dir}/pnpm" "${bin_dir}/docker" "${bin_dir}/curl"
  : > "${command_log}"
}

prepare_verify_all_fixture() {
  local fixture_dir="$1"

  mkdir -p "${fixture_dir}/scripts" "${fixture_dir}/backend" "${fixture_dir}/frontend/node_modules/.pnpm"
  cp "${ROOT_DIR}/scripts/verify-all.sh" "${fixture_dir}/scripts/verify-all.sh"
  chmod +x "${fixture_dir}/scripts/verify-all.sh"
  printf '{"dependencies":{"react":"^18.3.1","new-package":"^1.0.0"}}\n' > "${fixture_dir}/frontend/package.json"
  printf 'lockfileVersion: 9.0\nimporters:\n  .:\n    dependencies:\n      react:\n        specifier: ^18.3.1\n        version: 18.3.1\n' > "${fixture_dir}/frontend/pnpm-lock.yaml"
  cp "${fixture_dir}/frontend/pnpm-lock.yaml" "${fixture_dir}/frontend/node_modules/.pnpm/lock.yaml"
}

run_verify_all_with_stubs() {
  local fixture_dir="$1"
  local command_log="$2"

  local bin_dir="${fixture_dir}/stub-bin"
  write_stub_commands "${bin_dir}" "${command_log}"
  env VERIFY_ALL_STUB_LOG="${command_log}" PATH="${bin_dir}:${PATH}" "${fixture_dir}/scripts/verify-all.sh"
}

test_verify_all_invokes_quality_gates_with_stubs() {
  local tmp_dir
  local command_log

  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/verify-all-contract.XXXXXX")"
  command_log="${tmp_dir}/commands.log"
  prepare_verify_all_fixture "${tmp_dir}/repo"

  run_verify_all_with_stubs "${tmp_dir}/repo" "${command_log}"

  assert_log_contains "${command_log}" 'mvn package'
  assert_log_contains "${command_log}" 'pnpm install --frozen-lockfile --prefer-offline'
  assert_log_contains "${command_log}" 'pnpm lint'
  assert_log_contains "${command_log}" 'pnpm test'
  assert_log_contains "${command_log}" 'pnpm build'
  assert_log_contains "${command_log}" 'docker compose config --quiet'
  assert_log_contains "${command_log}" 'docker compose build'
  assert_log_contains "${command_log}" 'docker compose up -d --no-build'
  assert_log_contains "${command_log}" 'docker compose down --remove-orphans'
  assert_log_contains "${command_log}" 'curl -fsS http://localhost:8080/actuator/health'
  assert_log_contains "${command_log}" 'curl -fsS http://localhost:5173'

  rm -rf "${tmp_dir}"
}

test_verify_all_retries_cleanup_after_cleanup_failure() {
  local tmp_dir
  local command_log
  local status

  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/verify-all-contract.XXXXXX")"
  command_log="${tmp_dir}/commands.log"
  prepare_verify_all_fixture "${tmp_dir}/repo"
  : > "${command_log}.fail-down-once"

  set +e
  run_verify_all_with_stubs "${tmp_dir}/repo" "${command_log}"
  status="$?"
  set -e

  if (( status == 0 )); then
    printf 'cleanup 首次失败时 verify-all.sh 不应静默成功\n' >&2
    return 1
  fi
  assert_log_count_at_least "${command_log}" 'docker compose down --remove-orphans' 2

  rm -rf "${tmp_dir}"
}

bash -n "${ROOT_DIR}/scripts/verify-all.sh"
bash -n "${ROOT_DIR}/scripts/verify-backend.sh"
bash -n "${ROOT_DIR}/scripts/verify-frontend.sh"
bash -n "${ROOT_DIR}/scripts/verify-local-stack.sh"

assert_contains "${ROOT_DIR}/scripts/verify-all.sh" 'run_phase "后端测试"'
assert_contains "${ROOT_DIR}/scripts/verify-all.sh" 'run_phase "前端 lint"'
assert_contains "${ROOT_DIR}/scripts/verify-all.sh" 'run_phase "前端 vitest"'
assert_contains "${ROOT_DIR}/scripts/verify-all.sh" 'run_phase "前端 build"'
assert_contains "${ROOT_DIR}/scripts/verify-all.sh" 'run_phase "镜像构建"'
assert_contains "${ROOT_DIR}/scripts/verify-all.sh" 'run_phase "docker compose up"'
assert_contains "${ROOT_DIR}/scripts/verify-all.sh" 'run_phase "healthcheck / smoke test"'
assert_contains "${ROOT_DIR}/scripts/verify-all.sh" 'run_phase "docker compose down / cleanup"'
assert_contains "${ROOT_DIR}/scripts/verify-all.sh" 'docker compose up -d --no-build'
assert_contains "${ROOT_DIR}/scripts/verify-all.sh" '阶段耗时汇总'

assert_not_contains_regex "${ROOT_DIR}/scripts/verify-backend.sh" 'mvn test'
assert_not_contains_regex "${ROOT_DIR}/backend/Dockerfile" 'mvn .*package'
assert_not_contains_regex "${ROOT_DIR}/frontend/Dockerfile" 'pnpm (install|build)'

test_verify_all_invokes_quality_gates_with_stubs
test_verify_all_retries_cleanup_after_cleanup_failure

printf 'verify-all 性能契约检查通过\n'
