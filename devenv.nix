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
  languages.java.gradle.enable = enableAndroid; # Only needed for Android
  claude.code.enable = true;

  # Android development (conditional)
  android = lib.mkIf enableAndroid {
    enable = true;
    platforms.version = [ "34" ];
    systemImageTypes = [ "google_apis_playstore" ];
    abis = [
      "arm64-v8a"
      "x86_64"
    ];
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
    # Local development tunnel for webhook testing
    ngrok
  ];

  # Environment variables via dotenv for local development
  dotenv.enable = true;
  dotenv.filename = [ ".env" ];

  env.SECRETSPEC_PROVIDER = "dotenv://.env";
  env.SECRETSPEC_PROFILE = "default";

  # Handy scripts
  scripts."dev:up".exec = ''
    ./scripts/dev-up.sh
  '';
  scripts."dev:down".exec = ''
    ./scripts/dev-down.sh
  '';
  scripts."dev:logs".exec = ''
    ./scripts/dev-logs.sh
  '';
  scripts."dev:tui".exec = ''
    ./scripts/dev-tui.sh
  '';
  scripts."fmt:go".exec = "gofmt -s -w services";
  scripts."lint:go".exec = "golangci-lint run ./... || true";
  scripts."ci:deps".exec = ''
    ./scripts/ci-deps.sh
  '';
  scripts."ci:test".exec = ''
    ./scripts/ci-test.sh
  '';
  scripts."ci:lint".exec = ''
    ./scripts/ci-lint.sh
  '';
  scripts."ci:security".exec = ''
    ./scripts/ci-security.sh
  '';
  scripts."test:all".exec = ''
    ./scripts/test-all.sh
  '';
  scripts."test:coverage".exec = ''
    ./scripts/test-coverage.sh
  '';
  scripts."test:integration".exec = ''
    ./scripts/test-integration.sh
  '';
  scripts."android:emulator".exec = ''
    ./scripts/android-emulator.sh
  '';
  scripts."android:build".exec = ''
    ./scripts/android-build.sh
  '';
  scripts."android:install".exec = ''
    ./scripts/android-install.sh
  '';
  scripts."android:uninstall".exec = ''
    ./scripts/android-uninstall.sh
  '';
  scripts."android:run".exec = ''
    ./scripts/android-run.sh
  '';
  scripts."android:test".exec = ''
    ./scripts/android-test.sh
  '';
  scripts."android:test-unit".exec = ''
    ./scripts/android-test-unit.sh
  '';

  scripts."android:logs".exec = ''
    ./scripts/android-logs.sh
  '';
  scripts."schema:validate".exec = ''
    ./scripts/schema-validate.sh
  '';
  scripts."schema:generate".exec = ''
    ./scripts/schema-generate.sh
  '';
  scripts."schema:test".exec = ''
    ./scripts/schema-test.sh
  '';
  scripts."schema:sync".exec = ''
    ./scripts/schema-sync.sh
  '';
  scripts."test:schema-integration".exec = ''
    ./scripts/test-schema-integration.sh
  '';
  scripts."ci:full".exec = ''
    ./scripts/ci-full.sh
  '';

  # GCP deployment scripts
  scripts."gcp:auth".exec = ''
    ./scripts/gcp-auth.sh
  '';

  scripts."gcp:setup".exec = ''
    ./scripts/gcp-setup.sh
  '';

  scripts."gcp:db:setup".exec = ''
    ./scripts/gcp-db-setup.sh
  '';

  scripts."gcp:secrets:setup".exec = ''
    ./scripts/gcp-secrets-setup.sh
  '';

  scripts."gcp:deploy:verifier".exec = ''
    ./scripts/gcp-deploy-verifier.sh
  '';

  scripts."gcp:deploy:issuance-gateway".exec = ''
    ./scripts/gcp-deploy-issuance-gateway.sh
  '';

  scripts."env:switch".exec = ''
    set -euo pipefail
    ./scripts/env-switch.sh
  '';

  scripts."veriff:switch".exec = ''
    set -euo pipefail
    ./scripts/veriff-switch.sh
  '';

  scripts."gcp:status".exec = ''
    ./scripts/gcp-status.sh
  '';

  scripts."gcp:staging:logs".exec = ''
    ./scripts/gcp-staging-logs.sh
  '';

  scripts."gcp:staging:down".exec = ''
    ./scripts/gcp-staging-down.sh
  '';

  scripts."gcp:staging:up".exec = ''
    ./scripts/gcp-staging-up.sh
  '';

  scripts."gcp:test-deployment".exec = ''
    ./scripts/gcp-test-deployment.sh
  '';

  # Webhook development scripts using ngrok
  scripts."webhook:setup".exec = ''
    ./scripts/webhook-setup.sh
  '';

  scripts."webhook:tunnel".exec = ''
    ./scripts/webhook-tunnel.sh
  '';

  scripts."webhook:dev".exec = ''
    ./scripts/webhook-dev.sh
  '';

  # Run services with: `devenv up verifier registry receipts issuance-gateway`
  processes.verifier.exec = "bash -lc 'cd services/verifier && go run .'";
  processes.registry.exec = "bash -lc 'cd services/registry && go run .'";
  processes.receipts.exec = "bash -lc 'cd services/receipts-log && go run .'";
  processes.issuance-gateway.exec = "bash -lc 'cd services/issuance-gateway && go run .'";

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
          (pkgs.runCommand "workspace" { } ''
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
          (pkgs.runCommand "workspace" { } ''
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
          (pkgs.runCommand "workspace" { } ''
            mkdir -p $out/workspace
            cp -r ${./.} $out/workspace/
            chmod -R u+w $out/workspace
          '')
        ];
      };
    };

    # Issuance Gateway container
    issuance = {
      name = "cachet-issuance-gateway";
      startupCommand = pkgs.writeShellScript "start-issuance" ''
        #!/bin/bash
        set -euo pipefail
        export PORT=''${PORT:-8090}
        export ENVIRONMENT=''${ENVIRONMENT:-production}
        echo "Starting issuance gateway on port $PORT"
        cd /app
        # Build the binary first, then run it
        go build -o issuance-gateway ./services/issuance-gateway
        exec ./issuance-gateway
      '';
      registry = "";
      copyToRoot = pkgs.buildEnv {
        name = "container-root";
        paths = [
          (pkgs.runCommand "app-source" { } ''
            mkdir -p $out/app
            cp -r ${./.}/* $out/app/ || true
            cp -r ${./.}/.[^.]* $out/app/ || true
            chmod -R +w $out/app || true
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

      golangci-lint = {
        enable = true;
        name = "golangci-lint";
        entry = "./scripts/golangci-lint.sh";
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
    echo "✅ Cachet devenv ready with SecretSpec integration."
    echo "  Backend:"
    echo "    - Start services:   dev:up (wraps devenv up --detach)"
    echo "    - Stop services:    dev:down (or: devenv processes stop)"
    echo "    - Tail logs:        dev:logs"
    echo "    - Attach TUI:       dev:tui"
    echo "    - Format code:      fmt:go"
    echo "    - Lint (Go):        lint:go"
    echo "    - Test all:         test:all"
    echo "    - Test coverage:    test:coverage"
    echo "    - Integration test: test:integration"
    echo "  Android:"
    echo "    - Setup emulator:   android:emulator"
    echo "    - Switch env:       env:switch"
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
    echo "    - 🔻 Suspend staging:   gcp:staging:down"
    echo "    - 🔺 Resume staging:    gcp:staging:up"
    echo "    - 🧪 Test deployment:  gcp:test-deployment"
    echo "    - 🔑 Authenticate:     gcp:auth (if needed)"
    echo "  💡 Secrets managed via SecretSpec - local (.env) + production (Secret Manager)"
  '';
}
