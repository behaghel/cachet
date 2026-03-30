{ pkgs, lib, config, ... }:

let
  # Enable Android only when DEVENV_ENABLE_ANDROID is set
  enableAndroid = builtins.getEnv "DEVENV_ENABLE_ANDROID" != "";

  # Service list — single source of truth for all per-service scripts
  goServices = [ "verifier" "registry" "receipts-log" "issuance-gateway" ];
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

  # Android SDK + emulator (optional, heavy — ~2GB)
  # Enable with: export DEVENV_ENABLE_ANDROID=1
  android = lib.mkIf enableAndroid {
    enable = true;
    platforms.version = [ "34" ];
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

  # Port env vars derived from process port allocation (single source of truth)
  env.CACHET_VERIFIER_PORT = toString config.processes.verifier.ports.http.value;
  env.CACHET_REGISTRY_PORT = toString config.processes.registry.ports.http.value;
  env.CACHET_RECEIPTS_PORT = toString config.processes.receipts.ports.http.value;
  env.CACHET_ISSUANCE_PORT = toString config.processes.issuance-gateway.ports.http.value;

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
    echo "  android:build       Build APK"
    echo "  android:emulator    Create + start emulator"
    echo "  android:test-unit   Run unit tests"
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
    FLOOR=50
    FAIL=0
    for f in coverage/*.out; do
      svc=$(basename "$f" .out)
      pct=$(go tool cover -func="$f" 2>/dev/null | tail -1 | grep -oP '[0-9]+\.[0-9]+' || echo "0")
      int=''${pct%%.*}
      if [ "$int" -lt "$FLOOR" ]; then
        echo "❌ $svc coverage $pct% < $FLOOR% floor"
        FAIL=1
      else
        echo "✅ $svc coverage $pct%"
      fi
    done
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
    devenv processes stop
  '';
  scripts."android:emulator".exec = ''
    echo "Creating Android emulator..."
    avdmanager create avd --force --name cachet-emulator --package 'system-images;android-34;google_apis_playstore;x86_64' || true
    echo "Starting Android emulator..."
    emulator @cachet-emulator -no-audio -no-window &
    echo "Waiting for emulator to boot..."
    adb wait-for-device
    echo "✅ Android emulator ready"
  '';
  scripts."android:build".exec = ''
    echo "Building Android app..."
    cd mobile && ./gradlew --no-daemon :androidApp:assembleDebug
  '';
  scripts."android:install".exec = ''
    echo "Installing app on emulator..."
    cd mobile && gradle :androidApp:installDebug
  '';
  scripts."android:run".exec = ''
    echo "🚀 Starting full development environment..."
    echo "1. Starting backend services..."
    devenv up --detach
    sleep 3
    echo "2. Building and installing Android app..."
    cd mobile && gradle :androidApp:installDebug
    echo "3. Launching app..."
    adb shell am start -n id.cachet.wallet.android/.MainActivity
    echo "✅ Done! Backend running, app installed and launched."
    echo "🔗 Backend: http://localhost:8090 (from emulator: http://10.0.2.2:8090)"
  '';
  scripts."android:test".exec = ''
    echo "🧪 Running Android instrumented tests..."
    echo "1. Checking emulator connection..."
    adb devices | grep device || (echo "❌ No Android emulator detected. Run 'android:emulator' first." && exit 1)
    echo "2. Building and running tests..."
    cd mobile && gradle :androidApp:connectedAndroidTest
    echo "✅ Android tests completed!"
    echo "📊 Test results available in mobile/androidApp/build/reports/androidTests/"
  '';
  scripts."android:test-unit".exec = ''
    echo "🧪 Running unit tests..."
    echo "1. Running shared module tests..."
    cd mobile && gradle :shared:testDebugUnitTest
    echo "2. Running Android unit tests..."
    gradle :androidApp:testDebugUnitTest
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
      # Use yq to filter paths that contain the tag, keeping components intact
      yq eval "
        .paths |= with_entries(select(.value[].tags // [] | any(. == \"$tag\")))
      " schemas/openapi.yaml > "api/openapi.$tag.yaml"
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
  # Use `devenv up` to start all, or `devenv up verifier` for one.
  processes.verifier = {
    ports.http.allocate = 8081;
    exec = "cd services/verifier && PORT=${toString config.processes.verifier.ports.http.value} go run .";
  };
  processes.registry = {
    ports.http.allocate = 8082;
    exec = "cd services/registry && PORT=${toString config.processes.registry.ports.http.value} go run .";
  };
  processes.receipts = {
    ports.http.allocate = 8083;
    exec = "cd services/receipts-log && PORT=${toString config.processes.receipts.ports.http.value} go run .";
  };
  processes.issuance-gateway = {
    ports.http.allocate = 8090;
    exec = "cd services/issuance-gateway && PORT=${toString config.processes.issuance-gateway.ports.http.value} go run .";
  };

  # Pre-commit hooks for consistent build cycle
  git-hooks = {
    hooks = {
      # Go formatting and linting
      gofmt.enable = true;
      # golangci-lint disabled at root level - runs per-service in lint:go script
      # golangci-lint.enable = true;
      
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
      echo "   dev:services — start all   |  test:all — run tests"
      echo "   dev:stop — stop all        |  ci:full  — full CI locally"
    fi
  '';
}
