#!/usr/bin/env bash
set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "❌ jq is required for veriff:switch. Run this inside 'devenv shell'." >&2
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
  current=$(jq -r --arg env "$env_name" '.environments[$env].activeVeriffIntegration // .veriff.defaultIntegration // "(none)"' "$CONFIG_PATH")
  marker=""
  if [ "$env_name" = "$CURRENT_ENV" ]; then
    marker="*"
  fi
  printf "  [%d] %s%s (active integration: %s)\n" $((idx + 1)) "$env_name" "$marker" "$current"
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

mapfile -t INTEGRATIONS < <(jq -r '.veriff.integrations | keys[]' "$CONFIG_PATH")
if [ "${#INTEGRATIONS[@]}" -eq 0 ]; then
  echo "❌ No Veriff integrations defined in $CONFIG_PATH" >&2
  exit 1
fi

current_active=$(jq -r --arg env "$target_env" '.environments[$env].activeVeriffIntegration // ""' "$CONFIG_PATH")
if [ "$current_active" = "null" ]; then
  current_active=""
fi
default_integration=$(jq -r '.veriff.defaultIntegration // ""' "$CONFIG_PATH")
if [ -z "$current_active" ]; then
  current_active="$default_integration"
fi
if [ -z "$current_active" ]; then
  current_active=${INTEGRATIONS[0]}
fi

if [ -n "$current_active" ]; then
  printf "\nAvailable Veriff integrations for %s:\n" "$target_env"
else
  printf "\nAvailable Veriff integrations:\n"
fi
for idx in "${!INTEGRATIONS[@]}"; do
  integration=${INTEGRATIONS[$idx]}
  marker=""
  if [ "$integration" = "$current_active" ]; then
    marker="*"
  fi
  printf "  [%d] %s%s\n" $((idx + 1)) "$integration" "$marker"
done

echo -n "Select integration [$current_active]: "
read -r integration_selection || integration_selection=""
integration_selection=${integration_selection//[[:space:]]/}

target_integration=""
if [ -z "$integration_selection" ]; then
  target_integration="$current_active"
elif [[ "$integration_selection" =~ ^[0-9]+$ ]]; then
  idx=$((integration_selection - 1))
  if [ "$idx" -lt 0 ] || [ "$idx" -ge "${#INTEGRATIONS[@]}" ]; then
    echo "❌ Invalid integration selection '$integration_selection'" >&2
    exit 1
  fi
  target_integration=${INTEGRATIONS[$idx]}
else
  for integration in "${INTEGRATIONS[@]}"; do
    if [ "$integration" = "$integration_selection" ]; then
      target_integration="$integration"
      break
    fi
  done
  if [ -z "$target_integration" ]; then
    echo "❌ Integration '$integration_selection' not found" >&2
    exit 1
  fi
fi

updated=false
if [ "$target_integration" != "$current_active" ]; then
  tmp_file=$(mktemp)
  jq --indent 2 --arg env "$target_env" --arg integration "$target_integration" '.environments[$env].activeVeriffIntegration = $integration' "$CONFIG_PATH" > "$tmp_file"
  mv "$tmp_file" "$CONFIG_PATH"
  updated=true
  echo
  echo "✅ Updated $CONFIG_PATH: $target_env now uses Veriff integration '$target_integration'"
else
  echo
  echo "ℹ️  $target_env already configured for Veriff integration '$target_integration'; configuration file left unchanged."
fi

api_env=$(jq -r --arg integration "$target_integration" '.veriff.integrations[$integration].apiKeyEnv // ""' "$CONFIG_PATH")
if [ "$api_env" = "null" ]; then
  api_env=""
fi
webhook_env=$(jq -r --arg integration "$target_integration" '.veriff.integrations[$integration].webhookSecretEnv // ""' "$CONFIG_PATH")
if [ "$webhook_env" = "null" ]; then
  webhook_env=""
fi
base_url=$(jq -r --arg integration "$target_integration" '.veriff.integrations[$integration].baseUrl // ""' "$CONFIG_PATH")
if [ "$base_url" = "null" ]; then
  base_url=""
fi
global_webhook=$(jq -r --arg integration "$target_integration" '.veriff.integrations[$integration].webhookExternalUrl // ""' "$CONFIG_PATH")
if [ "$global_webhook" = "null" ]; then
  global_webhook=""
fi
env_webhook_override=$(jq -r --arg env "$target_env" '.environments[$env].veriffWebhookExternalUrl // ""' "$CONFIG_PATH")
if [ "$env_webhook_override" = "null" ]; then
  env_webhook_override=""
fi
webhook_external="$env_webhook_override"
if [ -z "$webhook_external" ]; then
  webhook_external="$global_webhook"
fi

public_url=$(jq -r --arg env "$target_env" '.environments[$env].services.issuanceGateway.publicUrl // ""' "$CONFIG_PATH")
if [ "$public_url" = "null" ]; then
  public_url=""
fi
emulator_url=$(jq -r --arg env "$target_env" '.environments[$env].services.issuanceGateway.emulatorUrl // ""' "$CONFIG_PATH")
if [ "$emulator_url" = "null" ] || [ -z "$emulator_url" ]; then
  emulator_url="$public_url"
fi

integration_slug=$(printf "%s" "$target_integration" | tr '[:upper:]' '[:lower:]')
api_secret_name="veriff-${integration_slug}-api-key"
webhook_secret_name="veriff-${integration_slug}-webhook-secret"

webhook_target="$webhook_external"
if [ -z "$webhook_target" ] && [ -n "$public_url" ]; then
  webhook_target="${public_url%/}/webhooks/veriff"
fi
if [ "$target_env" != "local" ] && command -v gcloud >/dev/null 2>&1; then
  gcloud_url=$(gcloud run services describe cachet-issuance-gateway --region=us-central1 --format='value(status.url)' 2>/dev/null || true)
  if [ -n "$gcloud_url" ]; then
    webhook_target="${gcloud_url%/}/webhooks/veriff"
  fi
fi

echo
printf "Configuration summary (env: %s)\n" "$target_env"
printf "  Active integration:     %s\n" "$target_integration"
if [ -n "$base_url" ]; then
  printf "  Veriff base URL:        %s\n" "$base_url"
fi
if [ -n "$public_url" ]; then
  printf "  Issuance public URL:    %s\n" "$public_url"
fi
if [ -n "$emulator_url" ]; then
  printf "  Emulator URL:           %s\n" "$emulator_url"
fi
if [ -n "$api_env" ]; then
  printf "  API key env var:        %s\n" "$api_env"
fi
if [ -n "$webhook_env" ]; then
  printf "  Webhook secret env var: %s\n" "$webhook_env"
fi
printf "  Secret Manager keys:    %s, %s\n" "$api_secret_name" "$webhook_secret_name"
if [ -n "$webhook_target" ]; then
  printf "  Webhook target URL:     %s\n" "$webhook_target"
fi

if [ "$target_env" = "local" ] || [ "$target_env" = "ci" ]; then
  echo
  echo "Next steps:"
  echo "  - Update your .env / CI secrets with the selected integration"
  if [ -n "$api_env" ]; then
    echo "      $api_env=<Veriff API key UUID>"
  fi
  if [ -n "$webhook_env" ]; then
    echo "      $webhook_env=<Veriff webhook signing secret>"
  fi
  if [ -n "$webhook_target" ]; then
    echo "  - Point the Veriff integration webhook to: $webhook_target"
  fi
  echo "  - Restart services: devenv shell -- dev:down && devenv shell -- dev:up"
else
  echo
  echo "Next steps:"
  echo "  1. Ensure Secret Manager has updated credentials:"
  echo "       printf 'uuid' | gcloud secrets versions add $api_secret_name --data-file=-"
  echo "       printf 'secret' | gcloud secrets versions add $webhook_secret_name --data-file=-"
  echo "  2. In Veriff Station, set the webhook to:"
  if [ -n "$webhook_target" ]; then
    echo "       $webhook_target"
  else
    echo "       <Cloud Run URL>/webhooks/veriff"
  fi
  echo "  3. Redeploy issuance gateway: devenv shell -- gcp:deploy:issuance-gateway"
fi

if [ "$updated" = true ]; then
  echo
  echo "💡 Commit the updated $CONFIG_PATH once you've validated the switch."
fi
