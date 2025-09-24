#!/usr/bin/env bash
set -euo pipefail

TMP_KEY=$(mktemp)
trap 'rm -f "$TMP_KEY"' EXIT

echo "$GCP_SA_KEY" | base64 -d > "$TMP_KEY"

gcloud auth activate-service-account --key-file "$TMP_KEY"
gcloud config set project cachet-staging >/dev/null

docker login -u _json_key --password-stdin https://gcr.io < "$TMP_KEY" >/dev/null

python3 - "$TMP_KEY" <<'PY'
import base64
import json
import pathlib
import sys

key_path = pathlib.Path(sys.argv[1])
password = key_path.read_text(encoding='utf-8')
auth = base64.b64encode(f"_json_key:{password}".encode()).decode()

payload = {"auths": {"https://gcr.io": {"auth": auth}, "gcr.io": {"auth": auth}}}

containers_config = pathlib.Path.home() / ".config" / "containers" / "auth.json"
containers_config.parent.mkdir(parents=True, exist_ok=True)
containers_config.write_text(json.dumps(payload), encoding="utf-8")
PY

if (( $# )); then
  exec "$@"
fi
