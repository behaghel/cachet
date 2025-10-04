#!/usr/bin/env bash
set -euo pipefail

if ! command -v golangci-lint >/dev/null 2>&1; then
  echo "golangci-lint not found in PATH. Run inside 'devenv shell'." >&2
  exit 1
fi

if [ $# -eq 0 ]; then
  echo "No Go files provided; skipping golangci-lint"
  exit 0
fi

declare -A seen_modules

find_module_root() {
  local path="$1"
  while [ "$path" != "." ] && [ "$path" != "" ]; do
    if [ -f "$path/go.mod" ]; then
      printf '%s\n' "$path"
      return
    fi
    path=$(dirname "$path")
  done
}

for file in "$@"; do
  if [ ! -f "$file" ]; then
    continue
  fi
  module=$(find_module_root "$(dirname "$file")")
  if [ -n "$module" ]; then
    seen_modules["$module"]=1
  fi
done

if [ ${#seen_modules[@]} -eq 0 ]; then
  echo "No Go modules found for staged files; running repo-wide lint"
  golangci-lint run ./...
  exit 0
fi

for module in "${!seen_modules[@]}"; do
  echo "golangci-lint: ${module}"
  (cd "$module" && golangci-lint run)
done
