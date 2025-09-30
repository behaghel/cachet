#!/usr/bin/env bash
set -euo pipefail

# Start managed processes with secrets injected from SecretSpec
unset CACHET_CONFIG_PATH
devenv processes stop >/dev/null 2>&1 || true
pkill -f "process-compose --config" >/dev/null 2>&1 || true
rm -f .devenv/processes.pid
rm -f .devenv/run/pc.sock
export CACHET_ENV="${CACHET_ENV:-local}"
secretspec run -- devenv up --detach verifier registry receipts issuance-gateway
