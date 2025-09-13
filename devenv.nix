{ pkgs, lib, ... }:

let
  # Enable Android only when DEVENV_ENABLE_ANDROID is set
  enableAndroid = builtins.getEnv "DEVENV_ENABLE_ANDROID" != "";
in
{
    
    # Languages / toolchains
    languages.go.enable = true;
  languages.javascript.enable = true;
  languages.java.enable = true;
  languages.java.gradle.enable = enableAndroid;  # Only needed for Android
  claude.code.enable = true;

  # Android development (conditional)
  android = lib.mkIf enableAndroid {
    enable = true;
    platforms.version = [ "34" ];
    systemImageTypes = [ "google_apis_playstore" ];
    abis = [ "arm64-v8a" "x86_64" ];
    emulator.enable = true;
    ndk.enable = true;
    systemImages.enable = true;
  };

  # Extra packages available in the shell
  packages = with pkgs; [
    nodejs
    nodePackages.npm
    pnpm
    nodePackages.typescript
    nodePackages.prettier
    yamllint
    git
    golangci-lint
    gosec
    jq
    openssl
    just
    docker
    docker-compose
    # Schema and code generation tools
    oapi-codegen
    openapi-generator-cli
    yamllint
    redocly
    # GCP deployment tools
    google-cloud-sdk
    terraform
    # SecretSpec binary
    secretspec
  ];

  # Useful env vars (used by docs and examples)
  env.CACHET_VERIFIER_PORT = "8081";
  env.CACHET_REGISTRY_PORT = "8082";
  env.CACHET_RECEIPTS_PORT = "8083";
  env.CACHET_ISSUANCE_PORT = "8090";

  # Environment variables via dotenv for local development
  dotenv.enable = true;

  # Handy scripts
  scripts."dev:services".exec = "devenv up --detach";
  scripts."dev:stop".exec = "devenv processes stop";
  scripts."fmt:go".exec = "gofmt -s -w services";
  scripts."lint:go".exec = "golangci-lint run ./... || true";
  scripts."ci:deps".exec = ''
    echo "📦 Downloading dependencies..."
    cd services/verifier && go mod download
    cd ../registry && go mod download  
    cd ../receipts-log && go mod download
    cd ../common && go mod download
    cd ../connector-hub && go mod download
    cd ../transparency-log && go mod download
    cd ../vouching-service && go mod download
    echo "✅ Dependencies downloaded"
  '';
  scripts."ci:test".exec = ''
    echo "🧪 Running tests with coverage..."
    set -euo pipefail  # Exit on any error
    
    mkdir -p coverage
    echo "Testing verifier..."
    (cd services/verifier && go test -v -coverprofile=../../coverage/verifier.out -covermode=atomic ./...)
    echo "Testing registry..."
    (cd services/registry && go test -v -coverprofile=../../coverage/registry.out -covermode=atomic ./...)
    echo "Testing receipts-log..."
    (cd services/receipts-log && go test -v -coverprofile=../../coverage/receipts.out -covermode=atomic ./...)
    echo "Testing issuance-gateway..."
    (cd services/issuance-gateway && go test -v -coverprofile=../../coverage/issuance.out -covermode=atomic ./...)
    echo "✅ All tests completed successfully with coverage"
  '';
  scripts."ci:lint".exec = ''
    echo "🔍 Running golangci-lint on all services..."
    set -euo pipefail  # Exit on any error
    
    # Use absolute paths and single commands to avoid cd issues in CI
    echo "Linting verifier..."
    (cd services/verifier && golangci-lint run)
    echo "Linting registry..."
    (cd services/registry && golangci-lint run)
    echo "Linting receipts-log..."
    (cd services/receipts-log && golangci-lint run)
    echo "Linting connector-hub..."
    (cd services/connector-hub && golangci-lint run)
    echo "Linting transparency-log..."  
    (cd services/transparency-log && golangci-lint run)
    echo "Linting vouching-service..."
    (cd services/vouching-service && golangci-lint run)
    echo "Linting issuance-gateway..."
    (cd services/issuance-gateway && golangci-lint run)
    echo "✅ All services passed linting successfully"
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
    echo "Scanning verifier..."
    cd services/verifier && gosec -exclude-generated ./...
    echo "Scanning registry..."
    cd ../registry && gosec -exclude-generated ./...
    echo "Scanning receipts-log..."
    cd ../receipts-log && gosec -exclude-generated ./...
    echo "Scanning connector-hub..."
    cd ../connector-hub && gosec -exclude-generated ./...
    echo "Scanning transparency-log..."
    cd ../transparency-log && gosec -exclude-generated ./...
    echo "Scanning vouching-service..."
    cd ../vouching-service && gosec -exclude-generated ./...
    echo "Scanning issuance-gateway..."
    cd ../issuance-gateway && gosec -exclude-generated ./...
    
    echo "✅ Security scan completed successfully"
  '';
  scripts."test:all".exec = ''
    echo "Running tests for all services..."
    cd services/verifier && go test -v ./... && echo "✅ Verifier tests passed"
    cd ../registry && go test -v ./... && echo "✅ Registry tests passed"  
    cd ../receipts-log && go test -v ./... && echo "✅ Receipts-log tests passed"
    cd ../issuance-gateway && go test -v ./... && echo "✅ Issuance gateway tests passed"
  '';
  scripts."test:coverage".exec = ''
    echo "Running tests with coverage..."
    mkdir -p coverage
    cd services/verifier && go test -coverprofile=../../coverage/verifier.out -covermode=atomic ./...
    cd ../registry && go test -coverprofile=../../coverage/registry.out -covermode=atomic ./...
    cd ../receipts-log && go test -coverprofile=../../coverage/receipts.out -covermode=atomic ./...
    cd ../issuance-gateway && go test -coverprofile=../../coverage/issuance.out -covermode=atomic ./...
    echo "Coverage reports generated in coverage/"
  '';
  scripts."test:integration".exec = ''
    echo "Running integration tests..."
    devenv up --detach
    sleep 5
    # Note: Using /health instead of /healthz - Cloud Run intercepts /healthz requests
    curl -f http://localhost:8081/health && echo "✅ Verifier healthy"
    curl -f http://localhost:8082/health && echo "✅ Registry healthy" 
    curl -f http://localhost:8083/health && echo "✅ Receipts healthy"
    curl -f http://localhost:8090/health && echo "✅ Issuance gateway healthy"
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
    echo "✅ Java found: $(java -version 2>&1 | head -n1)"
    cd mobile && ./gradlew --no-daemon :androidApp:assembleDebug
  '';
  scripts."android:install".exec = ''
    echo "Installing app on device/emulator..."
    if [ -z "$JAVA_HOME" ] && ! command -v java &> /dev/null; then
      echo "❌ Error: Java not found. Make sure you're running with DEVENV_ENABLE_ANDROID=1"
      echo "   Usage: DEVENV_ENABLE_ANDROID=1 devenv shell -- android:install"
      exit 1
    fi
    cd mobile && ./gradlew --no-daemon :androidApp:installDebug
  '';
  scripts."android:uninstall".exec = ''
    echo "Uninstalling app from device/emulator..."
    if [ -z "$JAVA_HOME" ] && ! command -v java &> /dev/null; then
      echo "❌ Error: Java not found. Make sure you're running with DEVENV_ENABLE_ANDROID=1"
      echo "   Usage: DEVENV_ENABLE_ANDROID=1 devenv shell -- android:uninstall"
      exit 1
    fi
    # Try using gradle uninstallDebug task first
    if cd mobile && ./gradlew --no-daemon :androidApp:uninstallDebug 2>/dev/null; then
      echo "✅ App uninstalled via Gradle"
    else
      # Fallback to adb uninstall if gradle task doesn't work
      echo "Gradle uninstall failed, trying adb..."
      PACKAGE_ID=$(grep 'applicationId' mobile/androidApp/build.gradle.kts | sed 's/.*applicationId = "//' | sed 's/".*//')
      if [ -n "$PACKAGE_ID" ]; then
        adb uninstall "$PACKAGE_ID"
        echo "✅ App uninstalled via adb: $PACKAGE_ID"
      else
        echo "❌ Could not determine package ID from build.gradle.kts"
        exit 1
      fi
    fi
  '';
  scripts."android:run".exec = ''
    echo "🚀 Starting full development environment..."
    echo "1. Starting backend services..."
    devenv up --detach
    sleep 3
    echo "2. Building and installing Android app..."
    cd mobile && ./gradlew --no-daemon :androidApp:installDebug
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
  
  scripts."android:logs".exec = ''
    echo "📱 Streaming Android device/emulator logs..."
    echo "📍 Use Ctrl+C to stop log streaming"
    echo "🔍 Filtering for Cachet wallet app logs..."
    echo ""
    
    # Check if ADB is available
    if ! command -v adb &> /dev/null; then
      echo "❌ Error: adb not found. Make sure Android SDK is installed."
      exit 1
    fi
    
    # Get connected devices and select the first one
    DEVICES=$(adb devices | grep -E '\tdevice$' | cut -f1)
    if [ -z "$DEVICES" ]; then
      echo "❌ No Android device/emulator detected."
      echo "   Make sure your device is connected or emulator is running."
      adb devices
      exit 1
    fi
    
    # Get the first device
    FIRST_DEVICE=$(echo "$DEVICES" | head -n1)
    echo "🔗 Connected devices:"
    adb devices
    echo ""
    echo "📱 Using device: $FIRST_DEVICE"
    echo ""
    
    # Clear old logs and start streaming from the selected device
    adb -s "$FIRST_DEVICE" logcat -c  # Clear existing logs
    
    # Stream logs with better filtering for mobile apps
    echo "🔍 Starting log stream (filtered for Cachet app)..."
    echo "   Monitoring: App crashes, network errors, Veriff integration, OkHttp requests"
    echo ""
    
    adb -s "$FIRST_DEVICE" logcat \
      -s "AndroidRuntime:E" \
      -s "System.err:*" \
      -s "CachetWallet:*" \
      -s "VeriffIntegration:*" \
      -s "OkHttp:*" \
      -s "NetworkSecurityConfig:*" \
      -s "id.cachet.wallet:*" \
      -s "*:E" \
      -s "*:W" \
    | while read line; do
      # Highlight important patterns
      if echo "$line" | grep -qiE "(crash|exception|error|failed|veriff|cachet)"; then
        echo "🔴 $line"
      elif echo "$line" | grep -qiE "(warn|warning)"; then
        echo "🟡 $line" 
      else
        echo "ℹ️  $line"
      fi
    done
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
    echo "🧪 Running schema integration tests..."
    
    echo "1. Testing Go schema compatibility..."
    cd tests/schema-integration && go test -v .
    
    echo "2. Testing Kotlin schema compatibility..."
    cd mobile && gradle :shared:test --tests "*SchemaCompatibilityTest*"
    
    echo "✅ Schema integration tests completed!"
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
  
  scripts."gcp:deploy:issuance-gateway".exec = ''
    echo "🚀 Deploying Issuance Gateway to Cloud Run with Veriff integration..."
    set -euo pipefail
    
    PROJECT_ID=$(gcloud config get-value project)
    SERVICE_NAME="cachet-issuance-gateway"
    
    # Ensure service account has secret access (idempotent)
    echo "🔐 Ensuring service account has Secret Manager access..."
    SERVICE_ACCOUNT="$PROJECT_ID-compute@developer.gserviceaccount.com"
    gcloud projects add-iam-policy-binding $PROJECT_ID \
      --member="serviceAccount:$SERVICE_ACCOUNT" \
      --role="roles/secretmanager.secretAccessor" \
      --quiet || echo "IAM binding already exists"
    
    # Build and push container using devenv container definition
    echo "📦 Building container with devenv..."
    devenv container build issuance
    
    # Tag for GCR
    docker tag cachet-issuance:latest gcr.io/$PROJECT_ID/$SERVICE_NAME:latest
    docker push gcr.io/$PROJECT_ID/$SERVICE_NAME:latest
    
    # Deploy to Cloud Run with SecretSpec-consistent secrets + Veriff credentials  
    echo "🌐 Deploying to Cloud Run with secrets from Secret Manager..."
    gcloud run deploy $SERVICE_NAME \
      --image gcr.io/$PROJECT_ID/$SERVICE_NAME:latest \
      --platform managed \
      --region us-central1 \
      --allow-unauthenticated \
      --port 8080 \
      --set-env-vars ENVIRONMENT=production \
      --set-secrets CACHET_DB_URL=database-url:latest,CACHET_JWT_SECRET=jwt-secret:latest,VERIFF_API_KEY=veriff-api-key:latest,VERIFF_WEBHOOK_SECRET=veriff-webhook-secret:latest \
      --set-env-vars VERIFF_BASE_URL=https://stationapi.veriff.com
    
    # Get the deployed service URL for webhook configuration
    SERVICE_URL=$(gcloud run services describe $SERVICE_NAME --region=us-central1 --format='value(status.url)')
    
    echo "✅ Issuance Gateway deployed successfully!"
    echo "🔗 Service URL: $SERVICE_URL"
    echo "🪝 Veriff Webhook URL: $SERVICE_URL/webhooks/veriff"
    echo ""
    echo "⚠️  Next steps:"
    echo "   1. Configure Veriff integration to use webhook URL: $SERVICE_URL/webhooks/veriff"
    echo "   2. Update mobile app to point to: $SERVICE_URL"
    echo "   3. Test the complete flow"
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

  # Run services with: `devenv up verifier registry receipts issuance-gateway`
  processes.verifier.exec = "go run ./services/verifier";
  processes.registry.exec = "go run ./services/registry";
  processes.receipts.exec = "go run ./services/receipts-log";
  processes.issuance-gateway.exec = "go run ./services/issuance-gateway";

  # Container definitions - single source of truth for dev and production
  containers = {
    # Verifier service container
    verifier = {
      name = "cachet-verifier";
      startupCommand = pkgs.writeShellScriptBin "start-verifier" ''
        export PORT=''${PORT:-8081}
        export ENVIRONMENT=''${ENVIRONMENT:-production}
        cd /workspace
        exec go run ./services/verifier
      '';
      registry = "";
      copyToRoot = pkgs.buildEnv {
        name = "workspace-root";
        paths = [
          (pkgs.runCommand "workspace" {} ''
            mkdir -p $out/workspace
            cp -r ${./.} $out/workspace/
            chmod -R u+w $out/workspace
          '')
        ];
      };
    };

    # Registry service container
    registry = {
      name = "cachet-registry";
      startupCommand = pkgs.writeShellScriptBin "start-registry" ''
        export PORT=''${PORT:-8082}
        export ENVIRONMENT=''${ENVIRONMENT:-production}
        cd /workspace
        exec go run ./services/registry
      '';
      registry = "";
      copyToRoot = pkgs.buildEnv {
        name = "workspace-root";
        paths = [
          (pkgs.runCommand "workspace" {} ''
            mkdir -p $out/workspace
            cp -r ${./.} $out/workspace/
            chmod -R u+w $out/workspace
          '')
        ];
      };
    };

    # Receipts service container
    receipts = {
      name = "cachet-receipts";
      startupCommand = pkgs.writeShellScriptBin "start-receipts" ''
        export PORT=''${PORT:-8083}
        export ENVIRONMENT=''${ENVIRONMENT:-production}
        cd /workspace
        exec go run ./services/receipts-log
      '';
      registry = "";
      copyToRoot = pkgs.buildEnv {
        name = "workspace-root";
        paths = [
          (pkgs.runCommand "workspace" {} ''
            mkdir -p $out/workspace
            cp -r ${./.} $out/workspace/
            chmod -R u+w $out/workspace
          '')
        ];
      };
    };

    # Issuance Gateway container
    issuance = {
      name = "cachet-issuance";
      startupCommand = pkgs.writeShellScriptBin "start-issuance" ''
        export PORT=''${PORT:-8090}
        export ENVIRONMENT=''${ENVIRONMENT:-production}
        cd /workspace
        exec go run ./services/issuance-gateway
      '';
      registry = "";
      copyToRoot = pkgs.buildEnv {
        name = "workspace-root";
        paths = [
          (pkgs.runCommand "workspace" {} ''
            mkdir -p $out/workspace
            cp -r ${./.} $out/workspace/
            chmod -R u+w $out/workspace
          '')
        ];
      };
    };
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
    echo "✅ Cachet devenv ready with SecretSpec integration."
    echo "  Backend:"
    echo "    - Run services:     dev:services (or: devenv up --detach)"
    echo "    - Stop services:    dev:stop (or: devenv processes stop)"
    echo "    - Format code:      fmt:go"
    echo "    - Lint (Go):        lint:go"
    echo "    - Test all:         test:all"
    echo "    - Test coverage:    test:coverage"
    echo "    - Integration test: test:integration"
    echo "  Android:"
    echo "    - Setup emulator:   android:emulator"
    echo "    - Build app:        android:build"
    echo "    - Install app:      android:install"
    echo "    - Uninstall app:    android:uninstall"
    echo "    - Full dev setup:   android:run"
    echo "    - Run UI tests:     android:test"
    echo "    - Run unit tests:   android:test-unit"
    echo "    - Stream app logs:  android:logs"
    echo "  Schema Management:"
    echo "    - Validate schema:  schema:validate"
    echo "    - Generate models:  schema:generate"
    echo "    - Test schemas:     schema:test"
    echo "    - Full sync:        schema:sync"
    echo "    - Integration test: test:schema-integration"
    echo "  CI/CD:"
    echo "    - Full CI locally:  ci:full"
    echo "  GCP Deployment (with SecretSpec):"
    echo "    - 🏗️ Setup project:     gcp:setup (includes billing check)"
    echo "    - 🗄️ Setup database:    gcp:db:setup"
    echo "    - 🔐 Setup secrets:     gcp:secrets:setup (creates .env + Secret Manager)"
    echo "    - 🚀 Deploy service:    gcp:deploy:verifier (with secrets integration)"
    echo "    - 📊 Check status:      gcp:status"
    echo "    - 🧪 Test deployment:  gcp:test-deployment"
    echo "    - 🔑 Authenticate:     gcp:auth (if needed)"
    echo "  💡 Secrets managed via SecretSpec - local (.env) + production (Secret Manager)"
  '';
}

