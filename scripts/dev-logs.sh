#!/usr/bin/env bash
set -euo pipefail

LOG_FILE=.devenv/processes.log
if [ ! -f "$LOG_FILE" ]; then
  echo "Processes log not found. Start services with dev:up first." >&2
  exit 1
fi
exec tail -f "$LOG_FILE"
