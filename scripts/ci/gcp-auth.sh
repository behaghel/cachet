#!/usr/bin/env bash
set -euo pipefail

TMP_KEY=$(mktemp)
trap 'rm -f "$TMP_KEY"' EXIT

echo "$GCP_SA_KEY" | base64 -d > "$TMP_KEY"

gcloud auth activate-service-account --key-file "$TMP_KEY"
gcloud config set project cachet-staging >/dev/null

ACCESS_TOKEN="$(gcloud auth print-access-token)"
AUTH_CONFIG="$(printf 'oauth2accesstoken:%s' "$ACCESS_TOKEN" | base64 | tr -d '\n')"

CONFIG_PATH="$HOME/.docker/config.json"
mkdir -p "$(dirname "$CONFIG_PATH")"
python3 -c 'import json, pathlib, sys; auth = sys.argv[1]; path = pathlib.Path.home() / ".docker" / "config.json"; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps({"auths": {"https://gcr.io": {"auth": auth}}}), encoding="utf-8")' "$AUTH_CONFIG"
