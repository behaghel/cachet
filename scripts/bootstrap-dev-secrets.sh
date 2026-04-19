#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${CACHET_ENV_FILE:-.env}"
WORKONRC_FILE="${CACHET_WORKONRC_FILE:-.workonrc}"
DEFAULT_DB_URL="${CACHET_DEFAULT_DB_URL:-postgresql://cachet:cachet@127.0.0.1:5432/cachet?sslmode=disable}"
DEFAULT_PROFILE_ENV="${CACHET_DEFAULT_PROFILE_ENV:-local}"
AUTO_CONFIRM=false
NON_INTERACTIVE=false

usage() {
  echo "Usage: $0 [--yes] [--non-interactive]"
  echo "  --yes, -y    Skip confirmation prompt"
  echo "  --non-interactive  Never prompt; print guidance and exit"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --yes|-y)
      AUTO_CONFIRM=true
      ;;
    --non-interactive)
      NON_INTERACTIVE=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
  shift
done

extract_value() {
  local file="$1"
  local key="$2"

  if [[ ! -f "$file" ]]; then
    return 0
  fi

  local value
  value=$(awk -F= -v key="$key" '
    $0 ~ "^[[:space:]]*(export[[:space:]]+)?" key "=" {
      sub("^[[:space:]]*(export[[:space:]]+)?" key "=", "", $0)
      print $0
      exit
    }
  ' "$file")

  value="${value#\"}"
  value="${value%\"}"
  value="${value#\'}"
  value="${value%\'}"

  printf '%s' "$value"
}

normalize_profile_env() {
  local profile_env="$1"
  case "$profile_env" in
    dev|development)
      printf '%s' "dev"
      ;;
    local|staging|prod|production)
      printf '%s' "$profile_env"
      ;;
    *)
      printf '%s' "local"
      ;;
  esac
}

runtime_env_from_profile() {
  local profile_env="$1"
  case "$profile_env" in
    local|dev)
      printf '%s' "development"
      ;;
    staging)
      printf '%s' "staging"
      ;;
    prod|production)
      printf '%s' "production"
      ;;
    *)
      printf '%s' "development"
      ;;
  esac
}

check_service_modules() {
  local missing=0
  local module_file

  for module_file in \
    "services/verifier/go.mod" \
    "services/registry/go.mod" \
    "services/receipts-log/go.mod" \
    "services/issuance-gateway/go.mod"; do
    if [[ ! -f "$module_file" ]]; then
      echo "⚠️  Missing service module file: $module_file"
      missing=1
    fi
  done

  if [[ $missing -eq 0 ]]; then
    echo "✅ Service module layout looks good for devenv processes."
  fi

  return "$missing"
}

sync_task_shims() {
  if [[ -x "./scripts/sync-devenv-task-shims.sh" ]]; then
    ./scripts/sync-devenv-task-shims.sh >/dev/null
    echo "✅ Direct task shims synced (.devenv/task-shims)."
  fi
}

is_yes() {
  local value="$1"
  [[ "$value" =~ ^([yY][eE][sS]|[yY])$ ]]
}

current_db_url="$(extract_value "$ENV_FILE" "CACHET_DB_URL")"
current_jwt_secret="$(extract_value "$ENV_FILE" "CACHET_JWT_SECRET")"
current_profile_env="$(extract_value "$ENV_FILE" "CACHET_ENV")"
current_runtime_env="$(extract_value "$ENV_FILE" "ENVIRONMENT")"
current_prompt_context="$(extract_value "$ENV_FILE" "CACHET_PROMPT_CONTEXT")"
current_google_project="$(extract_value "$ENV_FILE" "GOOGLE_CLOUD_PROJECT")"
current_cloudsdk_project="$(extract_value "$ENV_FILE" "CLOUDSDK_CORE_PROJECT")"
current_starship_label="$(extract_value "$WORKONRC_FILE" "STARSHIP_PROJECT_LABEL")"

missing_db_url=false
missing_jwt_secret=false
missing_profile_env=false
missing_runtime_env=false
missing_prompt_context=false
missing_starship_label=false

if [[ -z "$current_db_url" ]]; then
  missing_db_url=true
fi

if [[ -z "$current_jwt_secret" ]]; then
  missing_jwt_secret=true
fi

if [[ -z "$current_profile_env" ]]; then
  missing_profile_env=true
fi

if [[ -z "$current_runtime_env" ]]; then
  missing_runtime_env=true
fi

if [[ -z "$current_prompt_context" ]]; then
  missing_prompt_context=true
fi

if [[ -z "$current_starship_label" || "$current_starship_label" != "$current_prompt_context" ]]; then
  missing_starship_label=true
fi

if ! $missing_db_url && ! $missing_jwt_secret && ! $missing_profile_env && ! $missing_runtime_env && ! $missing_prompt_context && ! $missing_starship_label; then
  sync_task_shims
  check_service_modules
  exit 0
fi

gcloud_available=false
detected_gcloud_project=""
if command -v gcloud >/dev/null 2>&1; then
  gcloud_available=true
  detected_gcloud_project="$(gcloud config get-value project 2>/dev/null | tr -d '\r\n' || true)"
  if [[ "$detected_gcloud_project" == "(unset)" || "$detected_gcloud_project" == "[unset]" ]]; then
    detected_gcloud_project=""
  fi
fi

if ! $AUTO_CONFIRM; then
  if $NON_INTERACTIVE; then
    echo "Local development bootstrap is incomplete in $ENV_FILE / $WORKONRC_FILE."
    echo "Run ./scripts/bootstrap-dev-secrets.sh --yes to bootstrap automatically."
    exit 0
  elif [[ -t 0 && -t 1 ]]; then
    echo "🧭 Fresh development environment detected."
    echo "   Missing bootstrap values will be added to $ENV_FILE and $WORKONRC_FILE:"
    if $missing_db_url; then
      echo "   - CACHET_DB_URL"
    fi
    if $missing_jwt_secret; then
      echo "   - CACHET_JWT_SECRET"
    fi
    if $missing_profile_env; then
      echo "   - CACHET_ENV"
    fi
    if $missing_runtime_env; then
      echo "   - ENVIRONMENT"
    fi
    if $missing_prompt_context; then
      echo "   - CACHET_PROMPT_CONTEXT"
    fi
    if $missing_starship_label; then
      echo "   - STARSHIP_PROJECT_LABEL (shell prompt context)"
    fi

    if $gcloud_available; then
      if [[ -n "$detected_gcloud_project" ]]; then
        echo "   - Detected active gcloud project: $detected_gcloud_project"
      else
        echo "   - No active gcloud project detected (optional setup will be offered)"
      fi
    else
      echo "   - gcloud not found (GCP context setup will be skipped)"
    fi

    read -r -p "Proceed with automatic bootstrap? [y/N]: " confirm
    if ! is_yes "$confirm"; then
      echo "Skipped environment bootstrap. Run ./scripts/bootstrap-dev-secrets.sh when ready."
      exit 0
    fi
  else
    echo "Local development bootstrap is incomplete in $ENV_FILE / $WORKONRC_FILE."
    echo "Run ./scripts/bootstrap-dev-secrets.sh --yes to bootstrap automatically."
    exit 0
  fi
fi

db_url_value="$current_db_url"
jwt_secret_value="$current_jwt_secret"
profile_env_value="$current_profile_env"
runtime_env_value="$current_runtime_env"
prompt_context_value="$current_prompt_context"
gcloud_project_value="$current_google_project"
set_gcloud_project=false

if [[ -z "$gcloud_project_value" && -n "$current_cloudsdk_project" ]]; then
  gcloud_project_value="$current_cloudsdk_project"
fi
if [[ -z "$gcloud_project_value" && -n "$detected_gcloud_project" ]]; then
  gcloud_project_value="$detected_gcloud_project"
fi

if $missing_db_url; then
  db_url_value="$DEFAULT_DB_URL"
fi

if $missing_jwt_secret; then
  jwt_secret_value="$(openssl rand -hex 32 2>/dev/null || od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
fi

if [[ -z "$profile_env_value" ]]; then
  profile_env_value="$(normalize_profile_env "$DEFAULT_PROFILE_ENV")"
fi

if ! $AUTO_CONFIRM && ! $NON_INTERACTIVE && [[ -t 0 && -t 1 ]]; then
  profile_default="$profile_env_value"
  read -r -p "Select default environment [local/dev/staging/prod] (default: $profile_default): " selected_profile_env
  selected_profile_env="$(printf '%s' "$selected_profile_env" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
  if [[ -n "$selected_profile_env" ]]; then
    case "$selected_profile_env" in
      local|dev|development|staging|prod|production)
        profile_env_value="$(normalize_profile_env "$selected_profile_env")"
        ;;
      *)
        echo "Unknown environment '$selected_profile_env'; using '$profile_default'."
        profile_env_value="$profile_default"
        ;;
    esac
  fi

  if $gcloud_available; then
    if [[ -n "$gcloud_project_value" ]]; then
      read -r -p "Use gcloud project '$gcloud_project_value' for this repo? [Y/n]: " use_gcloud_project
      if [[ -n "$use_gcloud_project" ]] && ! is_yes "$use_gcloud_project"; then
        read -r -p "Enter gcloud project ID for this repo (leave blank to skip): " entered_gcloud_project
        gcloud_project_value="$(printf '%s' "$entered_gcloud_project" | tr -d '[:space:]')"
      fi
    else
      read -r -p "Enter gcloud project ID for this repo (leave blank to skip): " entered_gcloud_project
      gcloud_project_value="$(printf '%s' "$entered_gcloud_project" | tr -d '[:space:]')"
    fi

    if [[ -n "$gcloud_project_value" ]]; then
      read -r -p "Set active gcloud project to '$gcloud_project_value' now? [Y/n]: " confirm_set_gcloud
      if [[ -z "$confirm_set_gcloud" ]] || is_yes "$confirm_set_gcloud"; then
        set_gcloud_project=true
      fi
    fi
  fi
fi

runtime_env_value="$(runtime_env_from_profile "$profile_env_value")"

if [[ -n "$gcloud_project_value" ]]; then
  prompt_context_value="${profile_env_value}@${gcloud_project_value}"
else
  prompt_context_value="${profile_env_value}@local"
fi

tmp_file="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"

if [[ -f "$ENV_FILE" ]]; then
  grep -Ev '^[[:space:]]*(export[[:space:]]+)?(CACHET_DB_URL|CACHET_JWT_SECRET|CACHET_ENV|ENVIRONMENT|CACHET_PROMPT_CONTEXT|GOOGLE_CLOUD_PROJECT|CLOUDSDK_CORE_PROJECT)=' "$ENV_FILE" > "$tmp_file" || true
fi

if [[ ! -s "$tmp_file" ]]; then
  echo "# Auto-generated local development bootstrap" > "$tmp_file"
elif [[ "$(tail -c 1 "$tmp_file" 2>/dev/null || true)" != "" ]]; then
  echo "" >> "$tmp_file"
fi

echo "CACHET_DB_URL=\"$db_url_value\"" >> "$tmp_file"
echo "CACHET_JWT_SECRET=\"$jwt_secret_value\"" >> "$tmp_file"
echo "CACHET_ENV=\"$profile_env_value\"" >> "$tmp_file"
echo "ENVIRONMENT=\"$runtime_env_value\"" >> "$tmp_file"
echo "CACHET_PROMPT_CONTEXT=\"$prompt_context_value\"" >> "$tmp_file"

if [[ -n "$gcloud_project_value" ]]; then
  echo "GOOGLE_CLOUD_PROJECT=\"$gcloud_project_value\"" >> "$tmp_file"
  echo "CLOUDSDK_CORE_PROJECT=\"$gcloud_project_value\"" >> "$tmp_file"
fi

mv "$tmp_file" "$ENV_FILE"
chmod 600 "$ENV_FILE" 2>/dev/null || true

workon_tmp_file="$(mktemp "${WORKONRC_FILE}.tmp.XXXXXX")"
if [[ -f "$WORKONRC_FILE" ]]; then
  # Remove legacy STARSHIP_PROJECT_ENV and CACHET_PROMPT_CONTEXT (now lives in .env only)
  grep -Ev '^[[:space:]]*(export[[:space:]]+)?(STARSHIP_PROJECT_LABEL|STARSHIP_PROJECT_ENV|CACHET_PROMPT_CONTEXT)=' "$WORKONRC_FILE" > "$workon_tmp_file" || true
fi
if [[ ! -s "$workon_tmp_file" ]]; then
  echo "ASSIST_CMD=claude" > "$workon_tmp_file"
fi
if [[ "$(tail -c 1 "$workon_tmp_file" 2>/dev/null || true)" != "" ]]; then
  echo "" >> "$workon_tmp_file"
fi
echo "STARSHIP_PROJECT_LABEL=$prompt_context_value" >> "$workon_tmp_file"
mv "$workon_tmp_file" "$WORKONRC_FILE"

if $set_gcloud_project; then
  if gcloud config set project "$gcloud_project_value" >/dev/null 2>&1; then
    echo "☁️  Active gcloud project set to $gcloud_project_value"
  else
    echo "⚠️  Failed to set active gcloud project automatically."
    echo "   Run: gcloud config set project $gcloud_project_value"
  fi
fi

echo "✅ Local environment bootstrap complete."
echo "   - CACHET_DB_URL is configured"
echo "   - CACHET_JWT_SECRET is configured"
echo "   - CACHET_ENV=$profile_env_value"
echo "   - ENVIRONMENT=$runtime_env_value"
echo "   - Prompt context: $prompt_context_value"
if [[ -n "$gcloud_project_value" ]]; then
  echo "   - GCP project: $gcloud_project_value"
else
  echo "   - GCP project: not set (optional)"
fi

sync_task_shims
check_service_modules
