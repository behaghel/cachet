# Contributing to Cachet

Welcome! This guide covers how to contribute to the Cachet trust provider platform.

## 🚀 Quick Start

```bash
# Enter development environment
devenv shell

# Refresh direnv after .envrc updates
direnv allow

# Optional: force full use_devenv loading (instead of shim mode)
export USE_DEVENV=1
direnv reload

# If needed, bootstrap local environment (secrets + context)
dev:env:bootstrap

# Diagnose slow/stuck devenv shell startup
./scripts/diagnose-devenv-shell.sh

# Start services and run Android app
android:run

# Run all tests
test:all
android:test-unit
```

## 📋 Schema-First Development

All API changes must start with schema updates:

```bash
# 1. Edit OpenAPI schema
vim schemas/openapi.yaml

# 2. Validate and generate
schema:validate
schema:generate

# 3. Sync with codebase
schema:sync

# 4. Test everything
test:all
```

## 🔧 Development Workflow

1. **Create feature branch**: `git checkout -b feature/your-feature`
2. **Follow TDD**: Write tests first, implement, refactor
3. **Update schema**: If changing APIs, update `schemas/openapi.yaml`
4. **Quality checks**: Run `fmt:go`, `lint:go`, `test:all`
5. **Create PR**: Use conventional commits, detailed description

## 📊 Quality Gates

All PRs must pass:

- ✅ Schema validation
- ✅ All tests pass
- ✅ Code linting
- ✅ Security checks
- ✅ Generated models in sync

## 🧪 Testing

- **Unit tests**: `test:all` (Go), `android:test-unit` (Mobile)
- **Integration**: `test:integration`
- **E2E**: `android:test` (requires emulator)
- **Schema**: `schema:test`

## 📞 Getting Help

Use conventional commits; run linters; update OpenAPI.

For detailed guidelines, see our full documentation.
