#!/usr/bin/env bash
# Story 1.2 acceptance check: the local stack is healthy within TIMEOUT seconds (default 120).
#   scripts/stack-check.sh                  infrastructure only
#   scripts/stack-check.sh --profile apps   + mizan-api and mizan-analyzer
#   scripts/stack-check.sh --fresh ...      start from empty volumes (first-run path)
# Fails if `up --wait` times out, if any container is unhealthy or has exited
# (`--wait` treats an exited container without a healthcheck as ready), or if it took too long.
set -euo pipefail

TIMEOUT="${TIMEOUT:-120}"
FRESH=false
COMPOSE_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --fresh) FRESH=true; shift ;;
    --profile) COMPOSE_ARGS+=(--profile "$2"); shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "missing .env: cp .env.example .env" >&2; exit 2; }

# Stop every profile, not just the requested ones, so each run starts from the same state.
if $FRESH; then
  docker compose --profile '*' down -v --remove-orphans >/dev/null 2>&1
else
  docker compose --profile '*' down --remove-orphans >/dev/null 2>&1
fi

start=$(date +%s)
if ! docker compose "${COMPOSE_ARGS[@]}" up -d --wait --wait-timeout "$TIMEOUT"; then
  echo "FAIL: stack not healthy within ${TIMEOUT}s" >&2
  docker compose "${COMPOSE_ARGS[@]}" ps -a >&2
  exit 1
fi
elapsed=$(( $(date +%s) - start ))

# Give a worker that dies right after start-up a moment to show it.
sleep 5
bad=$(docker compose "${COMPOSE_ARGS[@]}" ps -a --format '{{.Service}} {{.State}} {{.Health}}' \
  | awk '$2 != "running" || ($3 != "" && $3 != "healthy")')
if [[ -n "$bad" ]]; then
  echo "FAIL: containers not running/healthy:" >&2
  echo "$bad" >&2
  exit 1
fi

docker compose "${COMPOSE_ARGS[@]}" ps --format '{{.Service}}: {{.Status}}'
if (( elapsed > TIMEOUT )); then
  echo "FAIL: healthy after ${elapsed}s (limit ${TIMEOUT}s)" >&2
  exit 1
fi
echo "PASS: healthy after ${elapsed}s (limit ${TIMEOUT}s)"
