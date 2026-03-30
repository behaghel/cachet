#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${CACHET_DEVENV_LOG_DIR:-.devenv/logs}"
mkdir -p "$LOG_DIR"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/devenv-shell-diagnose-$TIMESTAMP.log"
STAGE_TIMEOUT_SECONDS="${CACHET_DEVENV_STAGE_TIMEOUT_SECONDS:-300}"
PROGRESS_INTERVAL_SECONDS="${CACHET_DEVENV_PROGRESS_INTERVAL_SECONDS:-15}"

log() {
  local message="$1"
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $message" | tee -a "$LOG_FILE"
}

run_stage() {
  local stage_name="$1"
  local command="$2"
  local stage_started_at
  stage_started_at="$(date +%s)"
  local stage_log
  stage_log="$LOG_DIR/stage-$(echo "$stage_name" | tr ' :' '__')-$TIMESTAMP.log"
  : > "$stage_log"
  local stage_pipe
  stage_pipe="$LOG_DIR/stage-$(echo "$stage_name" | tr ' :' '__')-$TIMESTAMP.pipe"
  rm -f "$stage_pipe"
  mkfifo "$stage_pipe"
  local last_progress=""
  local warned_nodejs=false

  log "▶ START: $stage_name"
  log "  CMD: $command"
  log "  Live output will stream below"

  tee -a "$LOG_FILE" "$stage_log" < "$stage_pipe" &
  local tee_pid=$!

  (
    bash -lc "$command"
  ) > "$stage_pipe" 2>&1 &
  local command_pid=$!

  while kill -0 "$command_pid" 2>/dev/null; do
    sleep "$PROGRESS_INTERVAL_SECONDS"

    local now
    now="$(date +%s)"
    local elapsed=$((now - stage_started_at))

    if (( elapsed >= STAGE_TIMEOUT_SECONDS )); then
      log "⏱ TIMEOUT after ${STAGE_TIMEOUT_SECONDS}s: $stage_name"
      kill "$command_pid" 2>/dev/null || true
      break
    fi

    local progress
    progress=$(grep -E "Building [^[:cntrl:]]+|\[[0-9]+/[0-9]+\]" "$stage_log" | tail -n 1 || true)
    if [[ -n "$progress" && "$progress" != "$last_progress" ]]; then
      log "⏱ Progress (${elapsed}s): $progress"
      last_progress="$progress"
    else
      log "⏱ Still running (${elapsed}s): $stage_name"
    fi

    if ! $warned_nodejs && grep -Eq "Building .*nodejs|nodejs-[0-9]+" "$stage_log"; then
      log "⚠️  Detected local Node.js build from source; this can take a very long time."
      log "   Check cache config with: nix config show | rg 'substituters|trusted-public-keys'"
      log "   Expected cache includes https://cache.nixos.org/"
      warned_nodejs=true
    fi
  done

  local stage_status=0
  wait "$command_pid" || stage_status=$?
  wait "$tee_pid" 2>/dev/null || true
  rm -f "$stage_pipe"
  if [[ $stage_status -eq 0 ]]; then
    log "✅ DONE: $stage_name"
  else
    log "❌ FAIL: $stage_name (exit $stage_status)"
    return "$stage_status"
  fi
}

log "Devenv shell diagnostics started"
log "Log file: $LOG_FILE"
log "Stage timeout: ${STAGE_TIMEOUT_SECONDS}s"
log "Progress interval: ${PROGRESS_INTERVAL_SECONDS}s"

run_stage "devenv info" "devenv -v --log-format tracing-pretty info"
run_stage "devenv shell non-interactive warmup" "devenv -v --log-format tracing-pretty shell -- bash -lc 'echo shell-warmup-ok'"

log "Diagnostics completed successfully"
echo ""
echo "✅ Diagnostics finished."
echo "📄 Log file: $LOG_FILE"
