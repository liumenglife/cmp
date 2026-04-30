#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}/frontend"
if [[ -d node_modules ]] && [[ -f node_modules/.pnpm/lock.yaml ]] && cmp -s pnpm-lock.yaml node_modules/.pnpm/lock.yaml && pnpm install --frozen-lockfile --prefer-offline; then
  printf '前端依赖与 package.json / pnpm-lock.yaml 一致。\n'
else
  pnpm install --frozen-lockfile --prefer-offline
fi
pnpm lint
pnpm test
pnpm build
