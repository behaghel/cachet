#!/usr/bin/env bash
set -euo pipefail

PID_FILE=.devenv/processes.pid
if [ -f "$PID_FILE" ]; then
  PID=$(cat "$PID_FILE")
else
  PID=""
fi

devenv processes stop >/dev/null 2>&1 || true

if [ -n "$PID" ] && ps -p "$PID" >/dev/null 2>&1; then
  echo "Force stopping lingering devenv process $PID"
  kill "$PID" 2>/dev/null || true
  sleep 1
  if ps -p "$PID" >/dev/null 2>&1; then
    kill -9 "$PID" 2>/dev/null || true
  fi
fi

pkill -f "devenv:processes:" >/dev/null 2>&1 || true
pkill -f "secretspec run -- devenv up --detach" >/dev/null 2>&1 || true
pkill -f "process-compose --config" >/dev/null 2>&1 || true
rm -f "$PID_FILE"
rm -f .devenv/run/pc.sock
