#!/usr/bin/env bash
set -euo pipefail

TMP_KEY=$(mktemp)
trap 'rm -f "$TMP_KEY"' EXIT

echo "$GCP_SA_KEY" | base64 -d > "$TMP_KEY"

gcloud auth activate-service-account --key-file "$TMP_KEY"
gcloud config set project cachet-staging >/dev/null

ACCESS_TOKEN="$(gcloud auth print-access-token)"
AUTH_CONFIG="$(printf 'oauth2accesstoken:%s' "$ACCESS_TOKEN" | base64 | tr -d '\n')"

python3 - "$AUTH_CONFIG" <<'PY'
import json
import pathlib
import sys

auth = sys.argv[1]
payload = {"auths": {"https://gcr.io": {"auth": auth}}}

docker_config = pathlib.Path.home() / ".docker" / "config.json"
docker_config.parent.mkdir(parents=True, exist_ok=True)
docker_config.write_text(json.dumps(payload), encoding="utf-8")

containers_config = pathlib.Path.home() / ".config" / "containers" / "auth.json"
containers_config.parent.mkdir(parents=True, exist_ok=True)
containers_config.write_text(json.dumps(payload), encoding="utf-8")
PY
