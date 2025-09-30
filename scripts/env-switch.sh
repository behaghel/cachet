#!/usr/bin/env bash
set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "❌ jq is required for env:switch. Run this inside 'devenv shell'." >&2
  exit 1
fi

CONFIG_PATH=${CACHET_CONFIG_PATH:-config/app-config.json}
if [ ! -f "$CONFIG_PATH" ]; then
  echo "❌ Unable to locate configuration at $CONFIG_PATH" >&2
  exit 1
fi

DEFAULT_ENV=$(jq -r '.defaultEnvironment // "local"' "$CONFIG_PATH" 2>/dev/null || echo "local")
if [ -z "$DEFAULT_ENV" ] || [ "$DEFAULT_ENV" = "null" ]; then
  DEFAULT_ENV="local"
fi
CURRENT_ENV=${CACHET_ENV:-$DEFAULT_ENV}
if [ -z "$CURRENT_ENV" ] || [ "$CURRENT_ENV" = "null" ]; then
  CURRENT_ENV="$DEFAULT_ENV"
fi

mapfile -t ENVIRONMENTS < <(jq -r '.environments | keys[]' "$CONFIG_PATH")
if [ "${#ENVIRONMENTS[@]}" -eq 0 ]; then
  echo "❌ No environments defined in $CONFIG_PATH" >&2
  exit 1
fi

echo "Available Cachet environments:"
for idx in "${!ENVIRONMENTS[@]}"; do
  env_name=${ENVIRONMENTS[$idx]}
  marker=""
  if [ "$env_name" = "$CURRENT_ENV" ]; then
    marker="*"
  fi
  label=""
  case "$env_name" in
    local) label="local workstation" ;;
    ci) label="GitHub Actions (https://github.com)" ;;
    staging) label="cachet-staging (GCP)" ;;
    production) label="Not yet created" ;;
  esac
  if [ -n "$label" ]; then
    printf "  [%d] %s%s – %s\n" $((idx + 1)) "$env_name" "$marker" "$label"
  else
    printf "  [%d] %s%s\n" $((idx + 1)) "$env_name" "$marker"
  fi
done

echo -n "Select environment [$CURRENT_ENV]: "
read -r selection || selection=""
selection=${selection//[[:space:]]/}

target_env=""
if [ -z "$selection" ]; then
  target_env="$CURRENT_ENV"
elif [[ "$selection" =~ ^[0-9]+$ ]]; then
  idx=$((selection - 1))
  if [ "$idx" -lt 0 ] || [ "$idx" -ge "${#ENVIRONMENTS[@]}" ]; then
    echo "❌ Invalid environment selection '$selection'" >&2
    exit 1
  fi
  target_env=${ENVIRONMENTS[$idx]}
else
  for env_name in "${ENVIRONMENTS[@]}"; do
    if [ "$env_name" = "$selection" ]; then
      target_env="$env_name"
      break
    fi
  done
  if [ -z "$target_env" ]; then
    echo "❌ Environment '$selection' not found in $CONFIG_PATH" >&2
    exit 1
  fi
fi

if [ "$target_env" = "production" ]; then
  echo
  echo "❌ Production environment is not yet created. Selection remains $CURRENT_ENV." >&2
  echo "   Choose staging or local workflows instead." >&2
  exit 1
fi

active_veriff=$(jq -r --arg env "$target_env" '.environments[$env].activeVeriffIntegration // .veriff.defaultIntegration // empty' "$CONFIG_PATH" 2>/dev/null || echo "")
if [ "$active_veriff" = "null" ]; then
  active_veriff=""
fi

ENV_FILE=.env
tmp_file=$(mktemp)
new_file=false
if [ -f "$ENV_FILE" ]; then
  awk '!( $0 ~ /^[[:space:]]*CACHET_ENV=/ || $0 ~ /^[[:space:]]*ENVIRONMENT=/ )' "$ENV_FILE" > "$tmp_file"
else
  new_file=true
  : > "$tmp_file"
fi

if [ "$new_file" = true ]; then
  echo "# Cachet local environment selection (managed by env:switch)" >> "$tmp_file"
fi

if [ -s "$tmp_file" ]; then
  printf '\n' >> "$tmp_file"
fi
printf 'CACHET_ENV=%s\nENVIRONMENT=%s\n' "$target_env" "$target_env" >> "$tmp_file"

mv "$tmp_file" "$ENV_FILE"

echo
echo "✅ Updated .env with CACHET_ENV=$target_env"
if [ "$target_env" != "$CURRENT_ENV" ]; then
  echo "ℹ️  Previous environment was $CURRENT_ENV"
fi

echo
echo "Environment summary:"
printf "  Environment:        %s\n" "$target_env"
services_list=$(jq -r --arg env "$target_env" '.environments[$env].services | keys | join(", ")' "$CONFIG_PATH" 2>/dev/null || echo "")
if [ "$services_list" = "null" ]; then
  services_list=""
fi
if [ -n "$services_list" ]; then
  printf "  Services:           %s\n" "$services_list"
fi
case "$target_env" in
  local)
    note="Local workstation (localhost ports)"
    ;;
  ci)
    printf "  CI host:            https://github.com\n"
    note="Reserved for GitHub Actions workflows"
    ;;
  staging)
    printf "  GCP project:        cachet-staging\n"
    note="Shared staging stack in GCP"
    ;;
  *)
    note=""
    ;;
esac
if [ -n "$active_veriff" ]; then
  printf "  Veriff integration: %s\n" "$active_veriff"
fi
if [ -n "$note" ]; then
  printf "  Notes:              %s\n" "$note"
fi

echo
echo "Next steps:"
echo "  - Restart backend services if running: devenv shell -- dev:down && devenv shell -- dev:up"
if [ "$target_env" = "local" ] || [ "$target_env" = "ci" ]; then
  echo "  - Build the debug app: devenv shell -- android:build"
else
  echo "  - Build the release app: devenv shell -- android:build"
  echo "  - Install the release APK: devenv shell -- android:install"
fi
echo "  - Open a new 'devenv shell -- …' invocation so the updated .env is picked up"
