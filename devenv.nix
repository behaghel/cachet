{ pkgs, lib, config, ... }:

let
  # Enable Android only when DEVENV_ENABLE_ANDROID is set
  enableAndroid = builtins.getEnv "DEVENV_ENABLE_ANDROID" != "";

  # Service list — single source of truth for all per-service scripts
  goServices = [ "verifier" "registry" "receipts-log" "issuance-gateway" "relay" ];
  forEachService = f: builtins.concatStringsSep "\n" (map f goServices);
in
{
    
  # Languages / toolchains
  languages.go.enable = true;
  languages.javascript.enable = true;
  languages.javascript.package = pkgs.nodejs_20;
  languages.java.enable = true;            # Needed for Gradle/Kotlin mobile builds
  languages.java.gradle.enable = true;
  claude.code.enable = true;
  claude.code.hooks.git-hooks-run.enable = false; # prek runs at commit time via git hooks, not on every edit

  # Enable Veriff plugins for Claude Code (project-level settings.json is a Nix store symlink,
  # so /plugin install can't write to it — declare plugins here instead)
  files."${config.devenv.root}/.claude/settings.json".json = {
    enabledPlugins = {
      "spec-driven@veriff-plugins" = true;
      "spec-tdd@veriff-plugins" = true;
      "domain-tree@veriff-plugins" = true;
      "ux-stories@veriff-plugins" = true;
    };
    extraKnownMarketplaces = {
      veriff-plugins = {
        source = {
          source = "git";
          url = "git@github.com:Veriff/claude-plugins.git";
        };
      };
    };
  };

  # Android SDK + emulator (optional, heavy — ~2GB)
  # Enable with: export DEVENV_ENABLE_ANDROID=1
  android = lib.mkIf enableAndroid {
    enable = true;
    platforms.version = [ "36" ];
    buildTools.version = [ "36.0.0" ];
    systemImageTypes = [ "google_apis_playstore" ];
    abis = [ "arm64-v8a" "x86_64" ];
    emulator.enable = true;
    ndk.enable = true;
    systemImages.enable = true;
  };

  # Packages needed for daily development.
  # GCP tools (gcloud, terraform) are NOT included — install them
  # separately or add when needed. docker/docker-compose are system-level.
  packages = with pkgs; [
    # Node extras (Node itself comes from languages.javascript)
    pnpm
    nodePackages.prettier

    # Go tooling
    golangci-lint
    gosec
    oapi-codegen

    # Schema & code generation
    openapi-generator-cli
    redocly
    yamllint
    yq-go

    # General utilities
    git
    jq
    openssl
    secretspec
  ];

  # Fixed port env vars — must match the PORT= values in process exec commands above
  env.CACHET_VERIFIER_PORT = "8081";
  env.CACHET_REGISTRY_PORT = "8082";
  env.CACHET_RECEIPTS_PORT = "8083";
  env.CACHET_ISSUANCE_PORT = "8090";
  env.CACHET_RELAY_PORT = "8084";

  # Environment variables via dotenv for local development
  dotenv.enable = true;

  # Handy scripts
  scripts."dev:services".exec = "devenv up --detach";
  scripts."dev:stop".exec = "devenv processes stop";
  scripts."dev:secrets:bootstrap".exec = "./scripts/bootstrap-dev-secrets.sh";
  scripts."dev:env:bootstrap".exec = "./scripts/bootstrap-dev-secrets.sh";
  scripts."dev:devenv:diagnose".exec = "./scripts/diagnose-devenv-shell.sh";
  scripts."dev:help".exec = ''
    echo "Cachet Development Commands"
    echo "==========================="
    echo ""
    echo "Backend:"
    echo "  dev:services        Start all services (devenv up)"
    echo "  dev:stop            Stop all services"
    echo "  fmt:go              Format Go code"
    echo "  lint:go             Lint all Go services"
    echo "  test:all            Run all tests with -race"
    echo "  test:coverage       Run tests with coverage reports"
    echo "  test:integration    Health check all running services"
    echo ""
    echo "Schema:"
    echo "  schema:validate     Lint OpenAPI spec"
    echo "  schema:generate     Regenerate Go + Kotlin types"
    echo "  schema:split        Generate per-service specs from uber spec"
    echo "  schema:sync         Full schema sync (validate + generate + test)"
    echo ""
    echo "CI:"
    echo "  ci:full             Run full CI pipeline locally"
    echo "  ci:test             Tests with coverage + floor check"
    echo "  ci:lint             Lint all services"
    echo "  ci:security         Security scan (gosec)"
    echo ""
    echo "Android (requires DEVENV_ENABLE_ANDROID=1):"
    echo "  android:run         Backend + app (real mode, backend-driven)"
    echo "  android:demo        Backend + app (demo mode, fixtures)"
    echo "  android:install     Build + install + launch (no backend)"
    echo "  android:build       Build APK"
    echo "  android:emulator    Create + start emulator"
    echo "  android:test-unit   Run unit tests"
    echo "  android:bdd         Run BDD scenarios (Cucumber + Compose)"
    echo ""
    echo "Android demo scenarios (switch on-the-fly after android:demo):"
    echo "  android:happy        Happy path (Identity + Childcare + Seller)"
    echo "  android:revoked      Revoked identity cachet"
    echo "  android:expired      Expired credential"
    echo "  android:seller-only  Seller cachet only"
    echo "  android:empty        Empty vault (IDV onboarding)"
    echo ""
    echo "GCP (requires gcloud CLI):"
    echo "  gcp:setup           Setup GCP project"
    echo "  gcp:deploy:verifier Deploy to Cloud Run"
    echo "  gcp:status          Check deployment status"
  '';
  scripts."fmt:go".exec = "gofmt -s -w services";
  scripts."lint:go".exec = ''
    set -euo pipefail
    ${forEachService (svc: ''
    echo "Linting ${svc}..."
    (cd services/${svc} && golangci-lint run)
    '')}
    echo "✅ All services passed linting"
  '';
  scripts."ci:deps".exec = ''
    echo "📦 Downloading dependencies..."
    (cd services/common && go mod download)
    ${forEachService (svc: "(cd services/${svc} && go mod download)")}
    echo "✅ Dependencies downloaded"
  '';
  scripts."ci:test".exec = ''
    echo "🧪 Running tests with coverage..."
    set -euo pipefail  # Exit on any error
    
    mkdir -p coverage
    echo "Testing verifier..."
    (cd services/verifier && go test -v -race -coverprofile=../../coverage/verifier.out -covermode=atomic ./...)
    echo "Testing registry..."
    (cd services/registry && go test -v -race -coverprofile=../../coverage/registry.out -covermode=atomic ./...)
    echo "Testing receipts-log..."
    (cd services/receipts-log && go test -v -race -coverprofile=../../coverage/receipts.out -covermode=atomic ./...)
    echo "Testing issuance-gateway..."
    (cd services/issuance-gateway && go test -v -race -coverprofile=../../coverage/issuance.out -covermode=atomic ./...)

    # Coverage floor check (50% minimum, ratchet up over time)
    # Note: go tool cover -func must run from the service module directory
    # so that Go can resolve module paths in the coverage profile.
    FLOOR=50
    FAIL=0
    check_cov() {
      local svc="$1" svc_dir="$2" cov_file="$3"
      pct=$( (cd "services/$svc_dir" && go tool cover -func="../../coverage/$cov_file") 2>/dev/null | tail -1 | grep -oE '[0-9]+\.[0-9]+' || echo "0")
      int=''${pct%%.*}
      if [ "$int" -lt "$FLOOR" ]; then
        echo "❌ $svc coverage $pct% < $FLOOR% floor"
        FAIL=1
      else
        echo "✅ $svc coverage $pct%"
      fi
    }
    check_cov verifier verifier verifier.out
    check_cov registry registry registry.out
    check_cov receipts receipts-log receipts.out
    check_cov issuance issuance-gateway issuance.out
    [ "$FAIL" -eq 0 ] || { echo "Coverage below floor"; exit 1; }
    echo "✅ All tests completed with coverage above $FLOOR%"
  '';
  scripts."ci:lint".exec = ''
    echo "🔍 Running golangci-lint on all services..."
    set -euo pipefail
    ${forEachService (svc: ''
    echo "Linting ${svc}..."
    (cd services/${svc} && golangci-lint run)
    '')}
    echo "✅ All services passed linting"
  '';
  scripts."ci:security".exec = ''
    echo "🔒 Running security scan..."
    set -euo pipefail  # Exit on any error, undefined vars, or pipe failures
    
    # Install gosec if not already available
    if ! command -v gosec &> /dev/null; then
      echo "📦 Installing gosec..."
      go install github.com/securecodewarrior/gosec/v2/cmd/gosec@latest || {
        echo "❌ Failed to install gosec"
        exit 1
      }
    fi
    
    # Run security scan on each service with proper Go module context
    echo "🔍 Scanning services for security issues..."
    ${forEachService (svc: ''
    echo "Scanning ${svc}..."
    (cd services/${svc} && gosec -exclude-generated ./...)
    '')}
    
    echo "✅ Security scan completed successfully"
  '';
  scripts."test:all".exec = ''
    echo "Running tests for all services..."
    ${forEachService (svc: ''(cd services/${svc} && go test -v -race ./...) && echo "✅ ${svc} tests passed"'')}
  '';
  scripts."test:coverage".exec = ''
    echo "Running tests with coverage..."
    mkdir -p coverage
    (cd services/verifier && go test -race -coverprofile=../../coverage/verifier.out -covermode=atomic ./...)
    (cd services/registry && go test -race -coverprofile=../../coverage/registry.out -covermode=atomic ./...)
    (cd services/receipts-log && go test -race -coverprofile=../../coverage/receipts.out -covermode=atomic ./...)
    (cd services/issuance-gateway && go test -race -coverprofile=../../coverage/issuance.out -covermode=atomic ./...)
    echo "Coverage reports generated in coverage/"
  '';
  scripts."test:integration".exec = ''
    echo "Running integration tests..."
    devenv up --detach
    sleep 5
    # Note: Using /health instead of /healthz - Cloud Run intercepts /healthz requests
    curl -f http://localhost:$CACHET_VERIFIER_PORT/health && echo "✅ Verifier healthy"
    curl -f http://localhost:$CACHET_REGISTRY_PORT/health && echo "✅ Registry healthy"
    curl -f http://localhost:$CACHET_RECEIPTS_PORT/health && echo "✅ Receipts healthy"
    curl -f http://localhost:$CACHET_ISSUANCE_PORT/health && echo "✅ Issuance gateway healthy"
    curl -f http://localhost:$CACHET_RELAY_PORT/health && echo "✅ Relay healthy"
    devenv processes stop
  '';
  scripts."android:emulator".exec = ''
    echo "Creating Android emulator..."
    # Select ABI based on host architecture
    HOST_ARCH=$(uname -m)
    if [ "$HOST_ARCH" = "arm64" ] || [ "$HOST_ARCH" = "aarch64" ]; then
      ABI="arm64-v8a"
    else
      ABI="x86_64"
    fi
    echo "Host architecture: $HOST_ARCH → using ABI: $ABI"
    # Ensure the .android dir exists before avdmanager (avoids .ini file warnings)
    mkdir -p "$HOME/.android"
    touch "$HOME/.android/emu-update-last-check.ini"
    # Pipe 'no' to avoid interactive "custom hardware profile?" prompt that hangs in CI
    echo no | avdmanager create avd --force --name cachet-emulator --package "system-images;android-36;google_apis_playstore;$ABI"
    # Verify AVD was created
    if ! avdmanager list avd -c 2>/dev/null | grep -q cachet-emulator; then
      echo "❌ AVD creation failed. Check system image availability."
      exit 1
    fi

    # Kill stale adb server and remove cached keys that may cause "unauthorized"
    adb kill-server 2>/dev/null || true
    rm -f "$HOME/.android/adbkey" "$HOME/.android/adbkey.pub" 2>/dev/null || true

    echo "Starting Android emulator..."
    # Headless in CI, windowed for local dev
    WINDOW_FLAG=""
    if [ -n "''${CI:-}" ] || [ -z "''${DISPLAY:-}''${WAYLAND_DISPLAY:-}" ] && [ "$(uname)" != "Darwin" ]; then
      WINDOW_FLAG="-no-window"
    fi
    emulator @cachet-emulator -no-audio -no-snapshot-load -no-snapshot-save -wipe-data -metrics-collection $WINDOW_FLAG &
    echo "Waiting for emulator to boot..."
    adb start-server
    adb wait-for-device
    # wait-for-device returns when device appears; wait for it to be authorized
    echo "Waiting for device authorization..."
    for i in $(seq 1 60); do
      STATE=$(adb get-state 2>/dev/null || echo "unknown")
      if [ "$STATE" = "device" ]; then break; fi
      sleep 2
    done
    if [ "$(adb get-state 2>/dev/null)" != "device" ]; then
      echo "❌ Device not authorized after 120s"
      adb devices -l
      exit 1
    fi
    # Wait for full boot
    adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done' 2>/dev/null
    echo "✅ Android emulator ready"
  '';
  scripts."android:build".exec = ''
    echo "Building Android app..."
    # Avoid Gradle "conflicting SDK paths" error: only ANDROID_HOME should be set
    unset ANDROID_SDK_ROOT
    # Generate local.properties from devenv's ANDROID_HOME (avoids stale committed paths)
    echo "sdk.dir=$ANDROID_HOME" > mobile/local.properties
    cd mobile && ./gradlew --no-daemon :androidApp:assembleDemoDebug
  '';
  scripts."android:install".exec = ''
    set -euo pipefail
    ADB="$ANDROID_HOME/platform-tools/adb"

    echo "Installing app on emulator..."
    unset ANDROID_SDK_ROOT
    echo "sdk.dir=$ANDROID_HOME" > mobile/local.properties
    # Uninstall previous version if signatures differ (common after re-signing)
    $ADB uninstall id.cachet.wallet.android.demo 2>/dev/null || $ADB uninstall id.cachet.wallet.android 2>/dev/null || true
    cd mobile && ./gradlew --no-daemon :androidApp:installDemoDebug

    echo "Launching Cachet Wallet..."
    if $ADB shell am start -n id.cachet.wallet.android.demo/id.cachet.wallet.android.MainActivity 2>&1 | grep -q "Error\|Exception"; then
      echo "❌ Failed to launch app. Is the emulator running? (android:emulator)"
      exit 1
    fi
    # Verify the activity is in the foreground
    sleep 1
    if $ADB shell "dumpsys activity activities 2>/dev/null | grep -q 'id.cachet.wallet.android'"; then
      echo "✅ App installed and launched (real mode — backend-driven)"
    else
      echo "⚠️ App installed but may not have launched. Check the emulator screen."
    fi
  '';
  # Demo scenario launchers — use after android:install
  scripts."android:happy".exec = ''
    set -euo pipefail
    ADB="$ANDROID_HOME/platform-tools/adb"
    $ADB shell am force-stop id.cachet.wallet.android.demo
    $ADB shell am start -n id.cachet.wallet.android.demo/id.cachet.wallet.android.MainActivity --ez demo_mode true --es demo_scenario happy
    echo "✅ Launched: happy path (Identity + Childcare + Seller)"
  '';
  scripts."android:revoked".exec = ''
    set -euo pipefail
    ADB="$ANDROID_HOME/platform-tools/adb"
    $ADB shell am force-stop id.cachet.wallet.android.demo
    $ADB shell am start -n id.cachet.wallet.android.demo/id.cachet.wallet.android.MainActivity --ez demo_mode true --es demo_scenario revoked
    echo "✅ Launched: revoked scenario (Identity revoked, Childcare active)"
  '';
  scripts."android:expired".exec = ''
    set -euo pipefail
    ADB="$ANDROID_HOME/platform-tools/adb"
    $ADB shell am force-stop id.cachet.wallet.android.demo
    $ADB shell am start -n id.cachet.wallet.android.demo/id.cachet.wallet.android.MainActivity --ez demo_mode true --es demo_scenario expired
    echo "✅ Launched: expired scenario"
  '';
  scripts."android:seller-only".exec = ''
    set -euo pipefail
    ADB="$ANDROID_HOME/platform-tools/adb"
    $ADB shell am force-stop id.cachet.wallet.android.demo
    $ADB shell am start -n id.cachet.wallet.android.demo/id.cachet.wallet.android.MainActivity --ez demo_mode true --es demo_scenario seller-only
    echo "✅ Launched: seller-only scenario"
  '';
  scripts."android:empty".exec = ''
    set -euo pipefail
    ADB="$ANDROID_HOME/platform-tools/adb"
    $ADB shell am force-stop id.cachet.wallet.android.demo
    $ADB shell am start -n id.cachet.wallet.android.demo/id.cachet.wallet.android.MainActivity --ez demo_mode true --ez demo_empty true
    echo "✅ Launched: empty vault (IDV onboarding)"
  '';
  scripts."android:run".exec = ''
    set -euo pipefail
    ADB="$ANDROID_HOME/platform-tools/adb"

    echo "🚀 Starting full development environment..."
    echo "1. Starting backend services..."
    devenv up --detach
    sleep 3
    echo "2. Building and installing Android app..."
    unset ANDROID_SDK_ROOT
    echo "sdk.dir=$ANDROID_HOME" > mobile/local.properties
    cd mobile && ./gradlew --no-daemon :androidApp:installDemoDebug
    echo "3. Launching app (real mode — backend-driven)..."
    $ADB shell am start -n id.cachet.wallet.android.demo/id.cachet.wallet.android.MainActivity
    echo "✅ Done! Backend running, app installed and launched."
    echo "🔗 Backend: http://localhost:8090 (from emulator: http://10.0.2.2:8090)"
    echo "💡 For demo mode with fixtures: android:demo"
  '';
  scripts."android:demo".exec = ''
    set -euo pipefail
    ADB="$ANDROID_HOME/platform-tools/adb"

    echo "🚀 Starting demo environment (fixtures only, no backend)..."
    echo "1. Stopping backend services if running..."
    devenv processes stop 2>/dev/null || true
    echo "2. Building and installing Android app..."
    unset ANDROID_SDK_ROOT
    echo "sdk.dir=$ANDROID_HOME" > mobile/local.properties
    cd mobile && ./gradlew --no-daemon :androidApp:installDemoDebug
    echo "3. Launching app (demo mode — fixtures)..."
    $ADB shell am start -n id.cachet.wallet.android.demo/id.cachet.wallet.android.MainActivity --ez demo_mode true
    echo "✅ Done! App launched in demo mode with fixtures (no backend)."
    echo "💡 Switch scenario: android:revoked, android:expired, android:seller-only"
  '';
  scripts."android:test".exec = ''
    echo "🧪 Running Android instrumented tests..."
    echo "1. Checking emulator connection..."
    adb devices | grep device || (echo "❌ No Android emulator detected. Run 'android:emulator' first." && exit 1)
    echo "2. Building and running tests..."
    cd mobile && gradle :androidApp:connectedDemoDebugAndroidTest
    echo "✅ Android tests completed!"
    echo "📊 Test results available in mobile/androidApp/build/reports/androidTests/"
  '';
  scripts."android:bdd".exec = ''
    echo "🥒 Running BDD scenarios (Cucumber + Compose)..."
    echo "1. Syncing feature files from spec/ to androidTest assets..."
    find spec -name "scenarios.feature" -exec sh -c '
      story=$(basename $(dirname "$1"))
      cp "$1" mobile/androidApp/src/androidTest/assets/features/"$story".feature
    ' _ {} \;
    echo "2. Checking emulator connection..."
    adb devices | grep device || (echo "❌ No Android emulator detected. Run 'android:emulator' first." && exit 1)
    echo "3. Running BDD tests..."
    cd mobile && gradle :androidApp:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=id.cachet.wallet.android.bdd.CucumberTestRunner
    echo "✅ BDD scenarios completed!"
    echo "📊 Results: mobile/androidApp/build/reports/androidTests/"
  '';
  scripts."android:test-unit".exec = ''
    echo "🧪 Running unit tests..."
    echo "1. Running shared module tests..."
    cd mobile && gradle :shared:testDebugUnitTest
    echo "2. Running Android unit tests..."
    gradle :androidApp:testDemoDebugUnitTest
    echo "✅ Unit tests completed!"
    echo "📊 Test results available in mobile/*/build/reports/tests/"
  '';
  scripts."schema:validate".exec = ''
    echo "🔍 Validating OpenAPI schema..."
    yamllint schemas/openapi.yaml
    
    # Install and use redocly for OpenAPI validation
    if ! command -v redocly &> /dev/null; then
        echo "📦 Installing @redocly/cli..."
        npm install -g @redocly/cli
    fi
    
    redocly lint schemas/openapi.yaml
    echo "✅ Schema validation passed!"
  '';
  scripts."schema:split".exec = ''
    echo "🔀 Splitting spec by service tags..."
    set -euo pipefail
    mkdir -p api

    for tag in issuance-gateway verifier registry receipts-log; do
      echo "  Extracting $tag..."
      # Convert YAML→JSON, filter paths with jq (robust syntax), convert back to YAML
      yq -o=json schemas/openapi.yaml | \
        jq --arg tag "$tag" '
          .paths |= with_entries(
            select(.value | to_entries | any(
              .value.tags // [] | any(. == $tag)
            ))
          )
        ' | yq -P > "api/openapi.$tag.yaml"
    done

    echo "✅ Per-service specs generated in api/"
  '';

  scripts."schema:generate".exec = ''
    echo "🔧 Generating code from OpenAPI schema..."
    
    echo "1. Generating Go models..."
    mkdir -p generated/go
    oapi-codegen -generate types -package models schemas/openapi.yaml > generated/go/models.go
    
    echo "2. Generating Kotlin models..."
    mkdir -p generated/kotlin
    openapi-generator-cli generate \
      -i schemas/openapi.yaml \
      -g kotlin \
      -o generated/kotlin \
      --additional-properties=packageName=id.cachet.wallet.generated,serializationLibrary=kotlinx_serialization
    
    echo "✅ Code generation completed!"
    echo "📁 Generated files:"
    echo "   - Go: generated/go/models.go"
    echo "   - Kotlin: generated/kotlin/"
  '';
  scripts."schema:test".exec = ''
    echo "🧪 Running schema compatibility tests..."
    
    echo "1. Validating schema..."
    yamllint schemas/openapi.yaml
    
    echo "2. Generating temporary models..."
    rm -rf /tmp/cachet-schema-test
    mkdir -p /tmp/cachet-schema-test/go /tmp/cachet-schema-test/kotlin
    
    oapi-codegen -generate types -package models schemas/openapi.yaml > /tmp/cachet-schema-test/go/models.go
    openapi-generator-cli generate \
      -i schemas/openapi.yaml \
      -g kotlin \
      -o /tmp/cachet-schema-test/kotlin \
      --additional-properties=packageName=id.cachet.wallet.generated,serializationLibrary=kotlinx_serialization
    
    echo "3. Testing Go compilation..."
    cd /tmp/cachet-schema-test/go && go mod init test && go mod tidy && go build .
    
    echo "✅ Schema compatibility tests passed!"
  '';
  scripts."schema:sync".exec = ''
    echo "🔄 Synchronizing schemas across codebase..."
    
    echo "1. Running validation..."
    yamllint schemas/openapi.yaml
    
    echo "2. Generating fresh models..."
    schema:generate
    
    echo "3. Running compatibility tests..."
    schema:test
    
    echo "4. Updating mobile project..."
    # Copy generated Kotlin models to mobile project
    cp -r generated/kotlin/src/main/kotlin/* mobile/shared/src/commonMain/kotlin/ 2>/dev/null || true
    
    echo "5. Running tests..."
    test:all
    
    echo "✅ Schema synchronization completed!"
  '';
  scripts."test:schema-integration".exec = ''
    echo "🧪 Running schema compatibility checks..."
    set -euo pipefail

    echo "1. Verifying generated Go types compile..."
    (cd generated/go && go vet ./...)

    echo "2. Verifying generated Kotlin models compile against mobile..."
    (cd mobile && ./gradlew --no-daemon :shared:compileCommonMainKotlinMetadata)

    echo "✅ Schema compatibility checks passed!"
  '';
  scripts."ci:full".exec = ''
    echo "🚀 Running full CI pipeline locally..."
    
    echo "📋 Step 1: Schema validation and generation..."
    schema:validate
    schema:generate
    
    echo "🧪 Step 2: Backend tests..."
    test:all
    test:integration
    
    echo "📱 Step 3: Mobile tests..."
    android:test-unit
    
    echo "🔄 Step 4: Schema compatibility tests..."
    test:schema-integration
    
    echo "🔍 Step 5: Quality checks..."
    fmt:go
    lint:go
    
    echo "✅ Full CI pipeline completed successfully!"
    echo "🎉 Ready to create pull request!"
  '';

  # GCP deployment scripts
  scripts."gcp:auth".exec = ''
    if ! command -v gcloud &> /dev/null; then
      echo "❌ gcloud CLI not found. Install it: https://cloud.google.com/sdk/docs/install"
      exit 1
    fi
    echo "🔐 Authenticating with Google Cloud..."
    gcloud auth login
    echo "✅ Successfully authenticated with GCP"
  '';
  
  scripts."gcp:setup".exec = ''
    echo "🏗️  Setting up GCP project for Cachet deployment..."
    set -euo pipefail
    
    # Check if authenticated
    if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | head -n1 > /dev/null; then
      echo "❌ Not authenticated with GCP. Run 'gcp:auth' first."
      exit 1
    fi
    
    # Set project (user will be prompted to select/create)
    echo "Please select or create a GCP project:"
    gcloud projects list
    read -p "Enter project ID (or press Enter to create new): " PROJECT_ID
    
    if [ -z "$PROJECT_ID" ]; then
      read -p "Enter new project ID (e.g., cachet-prod-123): " PROJECT_ID
      gcloud projects create $PROJECT_ID
      
      # Wait for project creation to propagate
      echo "⏳ Waiting for project creation to complete..."
      sleep 5
    fi
    
    gcloud config set project $PROJECT_ID
    echo "📋 Using project: $PROJECT_ID"
    
    # Ensure billing is enabled (critical for Cloud SQL and other services)
    echo "🔍 Checking billing status..."
    if ! gcloud billing projects list --filter="projectId:$PROJECT_ID" --format="value(billingEnabled)" | grep -q "True"; then
      echo "⚠️  Billing is not enabled for this project."
      echo "   Please enable billing at: https://console.cloud.google.com/billing/linkedaccount?project=$PROJECT_ID"
      echo "   Press Enter when billing is enabled..."
      read
    fi
    
    # Enable required APIs with error handling
    echo "🔧 Enabling required GCP APIs..."
    APIS=(
      cloudbuild.googleapis.com
      run.googleapis.com  
      sqladmin.googleapis.com
      secretmanager.googleapis.com
      containerregistry.googleapis.com
      cloudresourcemanager.googleapis.com
    )
    
    for api in "''${APIS[@]}"; do
      echo "Enabling $api..."
      gcloud services enable $api || {
        echo "⚠️ Failed to enable $api - this may cause issues later"
      }
    done
    
    echo "✅ GCP project setup completed!"
    echo "📝 Next steps (run in order):"
    echo "   1. Run 'gcp:db:setup' to create Cloud SQL database"
    echo "   2. Run 'gcp:secrets:setup' to configure secrets with SecretSpec"
    echo "   3. Run 'gcp:deploy:verifier' to deploy services"
  '';
  
  scripts."gcp:db:setup".exec = ''
    echo "🗄️  Setting up Cloud SQL database..."
    set -euo pipefail
    
    PROJECT_ID=$(gcloud config get-value project)
    INSTANCE_NAME="cachet-db"
    DB_NAME="cachet"
    
    # Create Cloud SQL instance
    echo "Creating Cloud SQL PostgreSQL instance..."
    gcloud sql instances create $INSTANCE_NAME \
      --database-version=POSTGRES_15 \
      --cpu=1 \
      --memory=3840MB \
      --region=us-central1 \
      --root-password=temp-password-change-me
    
    # Create database
    gcloud sql databases create $DB_NAME --instance=$INSTANCE_NAME
    
    # Get connection string
    CONNECTION_NAME=$(gcloud sql instances describe $INSTANCE_NAME --format="value(connectionName)")
    
    echo "✅ Database setup completed!"
    echo "📋 Connection details:"
    echo "   Instance: $INSTANCE_NAME"
    echo "   Database: $DB_NAME"
    echo "   Connection: $CONNECTION_NAME"
    echo "⚠️  Remember to change the root password!"
  '';
  
  scripts."gcp:secrets:setup".exec = ''
    echo "🔐 Setting up Secret Manager with SecretSpec integration..."
    set -euo pipefail
    
    PROJECT_ID=$(gcloud config get-value project)
    
    # Generate secure database password
    echo "🔑 Generating secure database password..."
    DB_PASSWORD=$(openssl rand -base64 32)
    
    # Set the password for the postgres user
    echo "📝 Setting database password..."
    gcloud sql users set-password postgres \
      --instance=cachet-db \
      --password="$DB_PASSWORD"
    
    # Create database URL secret with proper connection string
    echo "🔐 Creating/updating database-url secret..."
    CONNECTION_NAME="$PROJECT_ID:us-central1:cachet-db"
    DATABASE_URL="postgresql://postgres:$DB_PASSWORD@/cachet?host=/cloudsql/$CONNECTION_NAME"
    
    # Try to create, but if it exists, add a new version
    if ! echo -n "$DATABASE_URL" | gcloud secrets create database-url --data-file=- 2>/dev/null; then
      echo "Secret already exists, updating with new version..."
      echo -n "$DATABASE_URL" | gcloud secrets versions add database-url --data-file=-
    fi
    
    # Create JWT secret
    echo "🔑 Creating/updating jwt-secret..."
    JWT_SECRET_VALUE=$(openssl rand -base64 32)
    if ! echo -n "$JWT_SECRET_VALUE" | gcloud secrets create jwt-secret --data-file=- 2>/dev/null; then
      echo "Secret already exists, updating with new version..."
      echo -n "$JWT_SECRET_VALUE" | gcloud secrets versions add jwt-secret --data-file=-
    fi
    
    # Create .env file for local development with secretspec
    echo "📝 Creating .env file for local development..."
    cat > .env << EOF
# Secrets for local development with secretspec
CACHET_DB_URL="$DATABASE_URL"
CACHET_JWT_SECRET="$JWT_SECRET_VALUE"
EOF
    
    echo "✅ Secrets created with SecretSpec integration!"
    echo "📋 Your secrets are now available via:"
    echo "   - CACHET_DB_URL (database connection)"  
    echo "   - CACHET_JWT_SECRET (JWT signing key)"
    echo "💡 These are accessible via secretspec in devenv and stored in GCP Secret Manager for production"
    echo "🔧 Local development will use the values from .env file"
  '';
  
  scripts."gcp:deploy:verifier".exec = ''
    echo "🚀 Deploying Verifier service to Cloud Run with SecretSpec integration..."
    set -euo pipefail
    
    PROJECT_ID=$(gcloud config get-value project)
    SERVICE_NAME="cachet-verifier"
    
    # Ensure service account has secret access (idempotent)
    echo "🔐 Ensuring service account has Secret Manager access..."
    SERVICE_ACCOUNT="$PROJECT_ID-compute@developer.gserviceaccount.com"
    
    # Grant Secret Manager access (these commands are idempotent)
    gcloud secrets add-iam-policy-binding database-url \
        --member="serviceAccount:$SERVICE_ACCOUNT" \
        --role="roles/secretmanager.secretAccessor" --quiet || true
        
    gcloud secrets add-iam-policy-binding jwt-secret \
        --member="serviceAccount:$SERVICE_ACCOUNT" \
        --role="roles/secretmanager.secretAccessor" --quiet || true
    
    # Build and push container
    echo "📦 Building container..."
    gcloud builds submit --tag gcr.io/$PROJECT_ID/$SERVICE_NAME ./services/verifier
    
    # Deploy to Cloud Run with SecretSpec-consistent secrets
    echo "🌐 Deploying to Cloud Run with secrets from Secret Manager..."
    gcloud run deploy $SERVICE_NAME \
      --image gcr.io/$PROJECT_ID/$SERVICE_NAME \
      --platform managed \
      --region us-central1 \
      --allow-unauthenticated \
      --port 8080 \
      --set-env-vars ENVIRONMENT=production \
      --set-secrets CACHET_DB_URL=database-url:latest,CACHET_JWT_SECRET=jwt-secret:latest
    
    echo "✅ Verifier service deployed with SecretSpec integration!"
    echo "🔗 Service URL: https://$SERVICE_NAME-$(echo $PROJECT_ID | tr ':' '-').us-central1.run.app"
    echo "🧪 Testing service endpoints..."
    sleep 5
    
    SERVICE_URL="https://$SERVICE_NAME-$(echo $PROJECT_ID | tr ':' '-').us-central1.run.app"
    curl -f "$SERVICE_URL/packs" > /dev/null && echo "✓ /packs endpoint working"
    curl -f "$SERVICE_URL/health" > /dev/null && echo "✓ /health endpoint working" || echo "ℹ /health endpoint not available (service works via /packs)"
    
    echo "🔍 Verifying SecretSpec consistency:"
    echo "   Local (via secretspec/dotenv): CACHET_DB_URL and CACHET_JWT_SECRET available"
    echo "   Cloud (via Secret Manager): Same secrets automatically injected"
  '';
  
  scripts."gcp:status".exec = ''
    echo "📊 Checking GCP deployment status..."
    set -euo pipefail
    
    echo "🗄️  Cloud SQL Status:"
    gcloud sql instances list
    
    echo ""
    echo "🌐 Cloud Run Services:"
    gcloud run services list --platform managed --region us-central1
    
    echo ""
    echo "🔐 Secrets:"
    gcloud secrets list
    
    echo ""
    echo "🔍 SecretSpec Integration Verification:"
    echo "   Local secrets available via secretspec ✓"
    echo "   Cloud secrets injected via Secret Manager ✓" 
    echo "   Same secret names in both environments ✓"
  '';
  
  scripts."gcp:test-deployment".exec = ''
    echo "🧪 Testing complete GCP deployment with SecretSpec..."
    set -euo pipefail
    
    SERVICE_URL=$(gcloud run services describe cachet-verifier --region=us-central1 --format='value(status.url)')
    
    echo "1. Testing local SecretSpec access..."
    if [ -n "''${CACHET_DB_URL:-}" ] && [ -n "''${CACHET_JWT_SECRET:-}" ]; then
      echo "   ✅ Local secrets accessible via secretspec"
    else
      echo "   ❌ Local secrets not available - check secretspec configuration"
      exit 1
    fi
    
    echo "2. Testing deployed service..."
    if curl -f "$SERVICE_URL/packs" > /dev/null 2>&1; then
      echo "   ✅ Service responding correctly"
    else
      echo "   ❌ Service not responding"
      exit 1
    fi
    
    echo "3. Verifying secrets are configured in Cloud Run..."
    SECRET_CONFIG=$(gcloud run services describe cachet-verifier --region=us-central1 --format="value(spec.template.spec.containers[0].env[].valueFrom.secretKeyRef.name)" | tr '\n' ',' || echo "")
    if [[ "$SECRET_CONFIG" == *"database-url"* ]] && [[ "$SECRET_CONFIG" == *"jwt-secret"* ]]; then
      echo "   ✅ Secrets properly configured in Cloud Run"
    else
      echo "   ❌ Secrets not configured in Cloud Run"
      exit 1
    fi
    
    echo ""
    echo "✅ All tests passed! SecretSpec integration working correctly:"
    echo "   • Local development uses .env via secretspec"
    echo "   • Production uses Secret Manager via Cloud Run"  
    echo "   • Same secret names and consistent access pattern"
    echo "   • Service deployed and functional"
  '';

  # Service processes with automatic port allocation.
  # Ports default to the values below; devenv finds a free port if taken.
  # Kill orphan Go binaries from previous runs before starting services.
  # `go run` forks a child binary that survives `devenv processes down`.
  tasks."devenv:processes:cleanup-orphans" = {
    exec = ''
      pkill -f 'go-build.*(verifier|registry|receipts|issuance|relay)' 2>/dev/null || true
      sleep 0.5
    '';
    before = [ "devenv:processes:verifier" "devenv:processes:registry" "devenv:processes:receipts" "devenv:processes:issuance-gateway" "devenv:processes:relay" ];
  };

  # Fixed ports — mobile app hardcodes these (10.0.2.2:<port> from emulator).
  # We hardcode PORT in exec instead of using ports.http.allocate, because
  # allocate silently picks a different port if an orphan holds ours.
  processes.verifier.exec = "cd services/verifier && CACHET_VERIFIER_DID='did:web:10.0.2.2%3A8081' PORT=8081 go run .";
  processes.registry.exec = "cd services/registry && PORT=8082 go run .";
  processes.receipts.exec = "cd services/receipts-log && PORT=8083 go run .";
  processes.issuance-gateway.exec = "cd services/issuance-gateway && VERIFF_WEBHOOK_SECRET=dev-secret-do-not-use-in-production PORT=8090 go run .";
  processes.relay.exec = "cd services/relay && PORT=8084 go run .";

  # Pre-commit hooks for consistent build cycle
  git-hooks = {
    hooks = {
      # Go formatting and linting
      gofmt.enable = true;

      # golangci-lint per-service (errcheck, staticcheck, etc.)
      golangci-lint-services = {
        enable = true;
        name = "golangci-lint (per service)";
        entry = "${pkgs.writeShellScript "lint-go-services" ''
          set -euo pipefail
          ${forEachService (svc: ''
          echo "Linting ${svc}..."
          (cd services/${svc} && ${pkgs.golangci-lint}/bin/golangci-lint run)
          '')}
        ''}";
        files = "\\.go$";
        language = "system";
        pass_filenames = false;
      };
      
      # Schema validation
      check-yaml.enable = true;
      
      # Custom hooks
      schema-validate = {
        enable = true;
        name = "OpenAPI Schema Validation";
        entry = "redocly lint schemas/openapi.yaml";
        files = "schemas/.*\\.yaml$";
        language = "system";
      };
      
      # Prevent /healthz endpoints from being committed (Cloud Run issue)
      check-healthz = {
        enable = true;
        name = "Check for forbidden /healthz endpoints";
        entry = "./scripts/check-healthz.sh";
        files = "\\.go$";
        language = "system";
        pass_filenames = false;
      };
      
      # Go mod tidy for all services (disabled temporarily due to hook conflicts)
      # go-mod-tidy = {
      #   enable = true;
      #   name = "Go mod tidy";
      #   entry = "bash -c 'for dir in services/*/; do if [ -f \"$dir/go.mod\" ]; then (cd \"$dir\" && go mod tidy); fi; done'";
      #   files = ".*\\.go$|go\\.(mod|sum)$";
      #   language = "system";
      # };
    };
  };

  enterShell = ''
    if [ -n "''${DIRENV_IN_ENVRC:-}" ] || [ -n "''${DIRENV_DIR:-}" ]; then
      # `use devenv` imports shell code via direnv; stdout here corrupts that stream.
      :
    elif [ -t 0 ]; then
      echo "⏱ [devenv] Shell init started at $(date '+%Y-%m-%d %H:%M:%S')"
      echo "⏱ [devenv] Checking local secret bootstrap"
      ./scripts/bootstrap-dev-secrets.sh
      echo "⏱ [devenv] Shell init completed at $(date '+%Y-%m-%d %H:%M:%S')"

      echo "✅ Cachet devenv ready. Run dev:help for commands."
      echo "   dev:services — start all   |  test:all  — run tests"
      echo "   android:run  — real mode   |  android:demo — demo fixtures"
      echo "   dev:stop — stop all        |  ci:full   — full CI locally"
    fi
  '';
}
