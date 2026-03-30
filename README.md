# Cachet

Creator-anchored trust provider with reusable, privacy-preserving **Trust Packs**.

## Quick start

- `devenv shell` (first run detects missing local bootstrap values and offers to set `.env`, prompt context, and optional `gcloud` defaults)
- `devenv run dev:compose` or `docker compose -f infra/docker-compose.yaml up --build`

After pulling `.envrc` changes, run `direnv allow` once. If full `devenv` loading fails, a fallback exposes direct task commands via `.devenv/task-shims`.
By default, direnv stays in this stable task-shim mode (keeps your normal shell prompt). Run `export USE_DEVENV=1; direnv reload` to opt into full `use devenv` loading.

If shell startup appears stuck, run `./scripts/diagnose-devenv-shell.sh` for live timestamped diagnostics with per-stage timeouts. If it reports local `nodejs` build, check Nix substituter/cache setup.

## Services

- verifier (packs list, verify presentations)
- registry (policy/pack registry)
- receipts-log (consent receipts + transparency log stub)

Generated: 2025-08-31T11:41:30Z
