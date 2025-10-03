#!/usr/bin/env bash
set -euo pipefail

if ! command -v golangci-lint >/dev/null 2>&1; then
  echo "golangci-lint not found in PATH. Run inside 'devenv shell'." >&2
  exit 1
fi

golangci-lint run ./...
