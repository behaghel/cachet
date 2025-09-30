#!/usr/bin/env bash
set -euo pipefail

echo "Building Android app..."

if [ -z "$JAVA_HOME" ] && ! command -v java &> /dev/null; then
  echo "❌ Error: Java not found. Make sure you're running with DEVENV_ENABLE_ANDROID=1"
  echo "   Usage: DEVENV_ENABLE_ANDROID=1 devenv shell -- android:build"
  exit 1
fi
if [ ! -f mobile/gradlew ]; then
  echo "❌ Error: gradlew not found in mobile directory"
  pwd
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "❌ jq is required for android:build. Run this inside 'devenv shell'."
  exit 1
fi

CONFIG_PATH="${CACHET_CONFIG_PATH:-config/app-config.json}"
if [ ! -f "$CONFIG_PATH" ]; then
  echo "❌ Unable to locate configuration at $CONFIG_PATH"
  exit 1
fi

DEFAULT_ENV=$(jq -r '.defaultEnvironment // "local"' "$CONFIG_PATH" 2>/dev/null || echo "local")
if [ -z "$DEFAULT_ENV" ] || [ "$DEFAULT_ENV" = "null" ]; then
  DEFAULT_ENV="local"
fi

SELECTED_ENV="${CACHET_ENV:-$DEFAULT_ENV}"
if [ -z "$SELECTED_ENV" ] || [ "$SELECTED_ENV" = "null" ]; then
  SELECTED_ENV="$DEFAULT_ENV"
fi

if ! jq -e --arg env "$SELECTED_ENV" '.environments[$env]' "$CONFIG_PATH" >/dev/null; then
  echo "❌ Environment '$SELECTED_ENV' not defined in $CONFIG_PATH"
  exit 1
fi

echo "🎯 Target environment: $SELECTED_ENV"

case "$SELECTED_ENV" in
  local|ci)
    echo "🛠️  Building debug variant for $SELECTED_ENV"
    (
      cd mobile || exit 1
      CACHET_ENV="$SELECTED_ENV" ./gradlew --no-daemon :androidApp:assembleDebug -PcachetEnv="$SELECTED_ENV"
    )
    APK_PATH=$(find mobile/androidApp/build/outputs/apk/debug -maxdepth 1 -type f -name "*debug*.apk" -print -quit 2>/dev/null || true)
    if [ -n "$APK_PATH" ]; then
      echo "✅ Debug APK ready: $APK_PATH"
    else
      echo "⚠️ Debug build finished but APK not found in mobile/androidApp/build/outputs/apk/debug"
    fi
    ;;
  *)
    echo "🚀 Building release variant for $SELECTED_ENV"

    ACTIVE_VERIFF=$(jq -r --arg env "$SELECTED_ENV" '.environments[$env].activeVeriffIntegration // .veriff.defaultIntegration // empty' "$CONFIG_PATH" 2>/dev/null || echo "")
    if [ "$ACTIVE_VERIFF" = "null" ]; then
      ACTIVE_VERIFF=""
    fi
    if [ -n "$ACTIVE_VERIFF" ]; then
      echo "🎯 Active Veriff integration: $ACTIVE_VERIFF"
    fi

    PUBLIC_URL=$(jq -r --arg env "$SELECTED_ENV" '.environments[$env].services.issuanceGateway.publicUrl // empty' "$CONFIG_PATH" 2>/dev/null || echo "")
    if [ "$PUBLIC_URL" = "null" ]; then
      PUBLIC_URL=""
    fi

    EMULATOR_URL=$(jq -r --arg env "$SELECTED_ENV" '.environments[$env].services.issuanceGateway.emulatorUrl // empty' "$CONFIG_PATH" 2>/dev/null || echo "")
    if [ "$EMULATOR_URL" = "null" ]; then
      EMULATOR_URL=""
    fi

    BASE_URL_OVERRIDE=""
    if command -v gcloud >/dev/null 2>&1; then
      GCLOUD_URL=$(gcloud run services describe cachet-issuance-gateway --region=us-central1 --format='value(status.url)' 2>/dev/null || echo "")
      if [ -n "$GCLOUD_URL" ]; then
        BASE_URL_OVERRIDE="$GCLOUD_URL"
        echo "✅ Targeting Cloud Run URL: $GCLOUD_URL"
      fi
    fi
    if [ -z "$BASE_URL_OVERRIDE" ]; then
      if [ -n "$PUBLIC_URL" ]; then
        BASE_URL_OVERRIDE="$PUBLIC_URL"
        echo "ℹ️  Using configured public URL: $PUBLIC_URL"
      elif [ -n "$EMULATOR_URL" ]; then
        BASE_URL_OVERRIDE="$EMULATOR_URL"
        echo "ℹ️  Using configured emulator URL: $EMULATOR_URL"
      else
        echo "❌ No issuance gateway URL available for $SELECTED_ENV"
        exit 1
      fi
    fi

    SIGNING_KEYSTORE="${CACHET_ANDROID_SIGNING_KEYSTORE:-$HOME/.android/debug.keystore}"
    SIGNING_STORE_PASSWORD="${CACHET_ANDROID_SIGNING_STORE_PASSWORD:-android}"
    SIGNING_KEY_ALIAS="${CACHET_ANDROID_SIGNING_KEY_ALIAS:-androiddebugkey}"
    SIGNING_KEY_PASSWORD="${CACHET_ANDROID_SIGNING_KEY_PASSWORD:-$SIGNING_STORE_PASSWORD}"

    SIGNING_ARGS_PRESENT=false
    if [ -f "$SIGNING_KEYSTORE" ]; then
      SIGNING_ARGS_PRESENT=true
      echo "🔐 Using signing keystore: $SIGNING_KEYSTORE"
    else
      echo "⚠️ Signing keystore not found at $SIGNING_KEYSTORE; release APK will be unsigned."
      echo "   Provide one via CACHET_ANDROID_SIGNING_KEYSTORE to get an installable build."
    fi

    (
      cd mobile || exit 1
      set -- --no-daemon :androidApp:assembleRelease "-PcachetEnv=$SELECTED_ENV" "-PcachetIssuanceBaseUrl=$BASE_URL_OVERRIDE"
      if [ "$SIGNING_ARGS_PRESENT" = true ]; then
        set -- "$@" \
          "-Pandroid.injected.signing.store.file=$SIGNING_KEYSTORE" \
          "-Pandroid.injected.signing.store.password=$SIGNING_STORE_PASSWORD" \
          "-Pandroid.injected.signing.key.alias=$SIGNING_KEY_ALIAS" \
          "-Pandroid.injected.signing.key.password=$SIGNING_KEY_PASSWORD"
      fi
      CACHET_ENV="$SELECTED_ENV" ./gradlew "$@"
    )

    APK_DIR="mobile/androidApp/build/outputs/apk/release"
    APK_PATH=$(find "$APK_DIR" -maxdepth 1 -type f -name "*release*.apk" ! -name "*-unsigned.apk" -print -quit 2>/dev/null || true)
    if [ -z "$APK_PATH" ]; then
      APK_PATH=$(find "$APK_DIR" -maxdepth 1 -type f -name "*.apk" -print -quit 2>/dev/null || true)
    fi

    if [ -n "$APK_PATH" ] && [ -f "$APK_PATH" ]; then
      echo "🎉 Release APK ready: $APK_PATH"
    else
      echo "⚠️ Release build finished but no APK found in $APK_DIR"
    fi
    ;;
esac
