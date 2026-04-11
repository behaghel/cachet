#!/usr/bin/env bash
# Contract test: validates that every pack ID in the client-side PackIds.kt
# exists as a JSON pack definition in the registry.
set -euo pipefail

PACK_IDS_FILE="mobile/androidApp/src/main/kotlin/id/cachet/wallet/android/ui/model/PackIds.kt"
PACKS_DIR="services/registry/internal/pack/packs"

if [[ ! -f "$PACK_IDS_FILE" ]]; then
  echo "ERROR: PackIds.kt not found at $PACK_IDS_FILE" >&2
  exit 1
fi

# Extract pack ID string values from const declarations
ids=$(grep -oP 'const val \w+ = "\K[^"]+' "$PACK_IDS_FILE")
failed=0

for id in $ids; do
  if ! grep -rql "\"id\": \"$id\"" "$PACKS_DIR" >/dev/null 2>&1; then
    echo "FAIL: $id (in PackIds.kt) not found in $PACKS_DIR" >&2
    failed=1
  fi
done

if [[ $failed -eq 1 ]]; then
  echo "Pack ID contract check FAILED. Add missing packs to the registry." >&2
  exit 1
fi

echo "Pack ID contract check passed ($(echo "$ids" | wc -l | tr -d ' ') IDs verified)"
