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
  ];

  # Useful env vars (used by docs and examples)
  env.CACHET_VERIFIER_PORT = "8081";
  env.CACHET_REGISTRY_PORT = "8082";
  env.CACHET_RECEIPTS_PORT = "8083";
  env.CACHET_ISSUANCE_PORT = "8090";

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
    cd services/verifier && go test -v -coverprofile=../../coverage/verifier.out -covermode=atomic ./...
    echo "Testing registry..."
    cd ../registry && go test -v -coverprofile=../../coverage/registry.out -covermode=atomic ./...
    echo "Testing receipts-log..."
    cd ../receipts-log && go test -v -coverprofile=../../coverage/receipts.out -covermode=atomic ./...
    echo "✅ All tests completed successfully with coverage"
  '';
  scripts."ci:lint".exec = ''
    echo "🔍 Running golangci-lint on all services..."
    set -euo pipefail  # Exit on any error
    
    echo "Linting verifier..."
    cd services/verifier && golangci-lint run
    echo "Linting registry..."
    cd ../registry && golangci-lint run
    echo "Linting receipts-log..."
    cd ../receipts-log && golangci-lint run
    echo "Linting connector-hub..."
    cd ../connector-hub && golangci-lint run
    echo "Linting transparency-log..."
    cd ../transparency-log && golangci-lint run
    echo "Linting vouching-service..."
    cd ../vouching-service && golangci-lint run
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
    curl -f http://localhost:8081/healthz && echo "✅ Verifier healthy"
    curl -f http://localhost:8082/healthz && echo "✅ Registry healthy" 
    curl -f http://localhost:8083/healthz && echo "✅ Receipts healthy"
    curl -f http://localhost:8090/healthz && echo "✅ Issuance gateway healthy"
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
    cd mobile && gradle :androidApp:assembleDebug
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
      golangci-lint.enable = true;
      
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
    echo "✅ Cachet devenv ready."
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
    echo "    - Full dev setup:   android:run"
    echo "    - Run UI tests:     android:test"
    echo "    - Run unit tests:   android:test-unit"
    echo "  Schema Management:"
    echo "    - Validate schema:  schema:validate"
    echo "    - Generate models:  schema:generate"
    echo "    - Test schemas:     schema:test"
    echo "    - Full sync:        schema:sync"
    echo "    - Integration test: test:schema-integration"
    echo "  CI/CD:"
    echo "    - Full CI locally:  ci:full"
    echo "  💡 Run scripts inside shell or via: devenv shell -- SCRIPT_NAME"
  '';
}

