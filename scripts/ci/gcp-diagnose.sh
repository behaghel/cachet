#!/usr/bin/env bash
set -euo pipefail

# Expect ACCESS_TOKEN to be exported by gcp-auth.sh
if [[ -z "${ACCESS_TOKEN:-}" ]]; then
  echo "ACCESS_TOKEN not set" >&2
  exit 1
fi

echo "::group::gcloud auth list"
gcloud auth list
printf 'Active account: %s\n' "$(gcloud config get-value account)"
echo "::endgroup::"

# Container Registry API probe via python (avoids leaking token)
python3 - "$ACCESS_TOKEN" <<'PY'
import json
import sys
import urllib.request

TOKEN = sys.argv[1]
ENDPOINTS = [
    "https://gcr.io/v2/",
    "https://gcr.io/v2/token?scope=repository:cachet-staging/cachet-issuance-gateway:push,pull",
    "https://gcr.io/v2/cachet-staging/cachet-issuance-gateway/tags/list",
]

for url in ENDPOINTS:
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {TOKEN}"})
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read(200)
            print(f"URL: {url}")
            print(f"  Status: {resp.status}")
            if body:
                print(f"  Body snippet: {body[:120]!r}")
    except Exception as exc:  # noqa: BLE001
        print(f"URL: {url}")
        print(f"  ERROR: {exc}")
PY

echo "::group::Current Docker auth config"
if [[ -f "$HOME/.docker/config.json" ]]; then
  cat "$HOME/.docker/config.json"
else
  echo "(missing)"
fi
echo "::endgroup::"

echo "::group::Current containers auth config"
if [[ -f "$HOME/.config/containers/auth.json" ]]; then
  cat "$HOME/.config/containers/auth.json"
else
  echo "(missing)"
fi
echo "::endgroup::"

# Try listing tags using gcloud (may emit useful IAM error messages)
set +e
GCLOUD_OUTPUT=$(gcloud container images list-tags gcr.io/cachet-staging/cachet-issuance-gateway --limit=1 2>&1)
GCLOUD_RC=$?
set -e

echo "::group::gcloud container images list-tags"
printf 'exit code: %s\n' "$GCLOUD_RC"
echo "$GCLOUD_OUTPUT"
echo "::endgroup::"

exit 0
