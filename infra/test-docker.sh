#!/usr/bin/env bash
# Smoke tests for Docker images and docker-compose orchestration.
# Requires a running Docker daemon (docker:start).
# Usage: devenv shell -- docker:test
set -euo pipefail

COMPOSE="docker compose -f infra/docker-compose.yaml"
PASS=0
FAIL=0
TESTS=()

pass() { PASS=$((PASS + 1)); TESTS+=("PASS  $1"); echo "  PASS  $1"; }
fail() { FAIL=$((FAIL + 1)); TESTS+=("FAIL  $1"); echo "  FAIL  $1"; }

cleanup() {
  echo ""
  echo "Cleaning up..."
  $COMPOSE down --timeout 5 2>/dev/null || true
}
trap cleanup EXIT

# --- Phase 1: Build ---
echo "=== Phase 1: Image builds ==="

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon not running. Run docker:start first."
  exit 1
fi

$COMPOSE build --quiet 2>&1
for svc in verifier registry receipts relay issuance-gateway admin; do
  if docker image inspect "infra-${svc}:latest" >/dev/null 2>&1; then
    pass "image build: $svc"
  else
    fail "image build: $svc"
  fi
done

# --- Phase 2: Image properties ---
echo ""
echo "=== Phase 2: Image properties ==="

for svc in verifier registry receipts relay issuance-gateway admin; do
  # Check that the image uses distroless (no shell)
  base=$(docker inspect "infra-${svc}:latest" --format '{{.Config.Entrypoint}}' 2>/dev/null || echo "")
  if [[ "$base" == *"/server"* ]]; then
    pass "entrypoint: $svc -> /server"
  else
    fail "entrypoint: $svc (got: $base)"
  fi

  # Check PORT=8080 env
  port=$(docker inspect "infra-${svc}:latest" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null | grep '^PORT=' || echo "")
  if [[ "$port" == "PORT=8080" ]]; then
    pass "env PORT=8080: $svc"
  else
    fail "env PORT=8080: $svc (got: $port)"
  fi

  # Check image is reasonably small (< 50MB — distroless + static Go binary)
  size=$(docker inspect "infra-${svc}:latest" --format '{{.Size}}' 2>/dev/null || echo "0")
  size_mb=$((size / 1024 / 1024))
  if [[ "$size_mb" -lt 50 ]]; then
    pass "image size: $svc (${size_mb}MB)"
  else
    fail "image size: $svc (${size_mb}MB > 50MB)"
  fi
done

# --- Phase 3: Compose up + health checks ---
echo ""
echo "=== Phase 3: Compose orchestration ==="

$COMPOSE up -d 2>&1

# Wait for services to be ready (max 15s)
echo "  Waiting for services..."
for i in $(seq 1 15); do
  ALL_UP=true
  for port in 8081 8082 8083 8084 8090 8091; do
    if ! curl -sf "http://localhost:$port/health" >/dev/null 2>&1; then
      ALL_UP=false
      break
    fi
  done
  if $ALL_UP; then break; fi
  sleep 1
done

# Port -> service name mapping
declare -A PORT_SVC=( [8081]=verifier [8082]=registry [8083]=receipts [8084]=relay [8090]=issuance-gateway [8091]=admin )

for port in 8081 8082 8083 8084 8090 8091; do
  svc="${PORT_SVC[$port]}"
  status=$(curl -sf "http://localhost:$port/health" 2>/dev/null || echo "")
  if [[ -n "$status" ]]; then
    pass "health :$port ($svc)"
  else
    fail "health :$port ($svc)"
  fi
done

# --- Phase 4: Service responses ---
echo ""
echo "=== Phase 4: Service responses ==="

# Verifier serves /packs
packs=$(curl -sf http://localhost:8081/packs 2>/dev/null || echo "")
if [[ -n "$packs" ]]; then
  pass "GET /packs (verifier)"
else
  fail "GET /packs (verifier)"
fi

# Registry serves /policy/manifest
manifest=$(curl -sf http://localhost:8082/policy/manifest 2>/dev/null || echo "")
if [[ -n "$manifest" ]]; then
  pass "GET /policy/manifest (registry)"
else
  fail "GET /policy/manifest (registry)"
fi

# Issuance gateway serves /.well-known/jwks.json
jwks=$(curl -sf http://localhost:8090/.well-known/jwks.json 2>/dev/null || echo "")
if echo "$jwks" | grep -q '"keys"' 2>/dev/null; then
  pass "GET /.well-known/jwks.json (issuance-gateway)"
else
  fail "GET /.well-known/jwks.json (issuance-gateway)"
fi

# Admin requires API key — unauthenticated should not return 200
admin_unauth=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/sessions 2>/dev/null || echo "000")
if [[ "$admin_unauth" != "200" ]]; then
  pass "admin rejects unauthenticated request ($admin_unauth)"
else
  fail "admin allows unauthenticated request (got 200)"
fi

# --- Phase 5: Inter-service connectivity ---
echo ""
echo "=== Phase 5: Inter-service connectivity ==="

# Verifier's /ready checks registry connectivity
ready=$(curl -sf http://localhost:8081/ready 2>/dev/null || echo "")
if [[ -n "$ready" ]]; then
  pass "verifier /ready (registry reachable)"
else
  fail "verifier /ready (registry unreachable?)"
fi

# --- Summary ---
echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
if [[ "$FAIL" -gt 0 ]]; then
  echo ""
  echo "Failures:"
  for t in "${TESTS[@]}"; do
    if [[ "$t" == FAIL* ]]; then echo "  $t"; fi
  done
  exit 1
fi
