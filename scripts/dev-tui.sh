#!/usr/bin/env bash
set -euo pipefail

SOCKET=$(readlink -f .devenv/run/pc.sock 2>/dev/null || true)
if [ -z "$SOCKET" ] || [ ! -S "$SOCKET" ]; then
  echo "Process Compose socket not found. Start services with dev:up first." >&2
  exit 1
fi
exec process-compose attach --unix-socket "$SOCKET"
