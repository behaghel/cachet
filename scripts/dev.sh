#!/usr/bin/env bash
set -euo pipefail
docker compose -f infra/docker-compose.yaml up --build
