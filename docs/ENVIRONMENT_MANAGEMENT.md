# Environment & Veriff Operations

This runbook captures the environment conventions that came out of the recent staging investigation. It explains how `CACHET_ENV`, the shared app-config, Veriff integrations, and automation fit together so every deploy or build follows the same contract.

## 1. Canonical Configuration Source

- `config/app-config.json` defines every environment (`local`, `staging`, `production`, `ci`).
- Each environment block declares service hosts/ports. Veriff configuration lives once under the top-level `veriff.integrations` map, with per-environment links provided by `activeVeriffIntegration` (defaults to `veriff.defaultIntegration` when omitted).
- Environments can override the callback endpoint with `veriffWebhookExternalUrl`; otherwise the loader falls back to the issuance gateway public URL.
- `services/common/config` loads this file using `CACHET_ENV` (defaults to `defaultEnvironment`).
- All backend binaries now log their resolved configuration at startup so you can confirm the active environment, ports, and URLs from Cloud Run / local logs without SSHing in.

## 2. Environment Variables

| Variable | Purpose |
| --- | --- |
| `CACHET_ENV` | Selects the environment block in `app-config.json`. Scripts set this automatically (`dev:up`, `env:switch`, `android:build`, `gcp:deploy:issuance-gateway`). |
| `ENVIRONMENT` | Secondary hint used by services for logging/formatting (falls back to `CACHET_ENV`). |
| `VERIFF_ENVIRONMENT` | Optional override that forces a specific integration name. Normally stays unset in Cloud Run so we rely on the config file. |

### Automation touchpoints

- `dev:up` exports `CACHET_ENV=local` before launching services with SecretSpec.
- `env:switch` persists the target environment to `.env` so subsequent commands run with the same setting.
- `android:build` reads the selected environment and switches between debug (local/ci) and release (staging/production) builds automatically.
- `gcp:deploy:issuance-gateway` reads the staging block, injects `VERIFF_ENVIRONMENT`, `VERIFF_BASE_URL`, and expects the matching Secret Manager entries.

## 3. Switching Environments (`env:switch`)

`devenv shell -- env:switch` is the canonical helper for selecting the active environment.

1. Reads every environment declared in `config/app-config.json` and highlights the current selection.
2. Prompts for the new target, then rewrites `.env` with `CACHET_ENV=<env>` and `ENVIRONMENT=<env>` so the setting persists across shells.
3. Prints a summary of all backend services plus the active Veriff integration so you can double-check what the stack will target.
4. Suggests follow-up commands; open a fresh `devenv shell -- …` session afterwards so the updated `.env` is loaded.

With the environment locked in, downstream automation stays in sync:

- `android:build` chooses debug vs. release, injects the right Gradle properties, and fetches Cloud Run URLs when available.
- `android:install` installs the matching variant (debug for local/ci, release for staging/production).
- `dev:up` continues to start services with the selected `CACHET_ENV`, so logs and SecretSpec both align.

The selector annotates each option so you know where it runs:

- `local` — workstation defaults (localhost ports).
- `ci` — reserved for GitHub Actions (`https://github.com`).
- `staging` — deployed in the `cachet-staging` GCP project.
- `production` — currently disabled; the helper rejects the choice and leaves `.env` untouched until that tier is created.

### Debugging Veriff Sessions

While you are developing locally (or when `CACHET_DEBUG=1` is exported), the issuance gateway exposes two read-only helpers so you can inspect what the automation cached:

- `GET /debug/veriff/sessions` — lists every session the webhook stored, its status, quality tier, and vault snapshot timestamp.
- `GET /debug/veriff/sessions/{sessionId}` — returns the full `VeriffSession` payload, the enhanced validation profile (including the computed score), and the vault artifact/predicates that will be embedded in the credential response.

These endpoints are disabled in staging/production unless you opt-in with `CACHET_DEBUG`, and they never include decrypted sensitive data—only the sanitized artifacts we already return in the credential payload.

## 4. Switching Veriff Integrations (`veriff:switch`)

`devenv shell -- veriff:switch` is the canonical helper. It

1. Reads available environments/integrations from `config/app-config.json`.
2. Prompts for the target environment (defaults to the current `CACHET_ENV`).
3. Prompts for the integration, updates `activeVeriffIntegration` when it changes, and prints the sanitized configuration summary.
4. Lists the environment variables, base URLs, webhook target (including any environment override), and the Secret Manager key names the deployment scripts expect.
5. Gives tailored next steps:
   - **local/ci** – update `.env` / CI secrets, restart `dev:up`.
   - **staging/production** – rotate secrets (`veriff-<integration>-api-key`, `veriff-<integration>-webhook-secret`), update Veriff Station to the printed webhook URL, redeploy via `devenv shell -- gcp:deploy:issuance-gateway`.

The helper also guards against missing GCP secrets—`gcp:deploy:issuance-gateway` will refuse to deploy until the integration-specific secrets exist, making drift visible early.

## 5. Secret Layout & Naming

| Integration | API key secret | Webhook secret | Env vars loaded in service |
| --- | --- | --- | --- |
| `test` | `veriff-test-api-key` | `veriff-test-webhook-secret` | `VERIFF_API_KEY`, `VERIFF_TEST_API_KEY` (fallback), `VERIFF_WEBHOOK_SECRET`, `VERIFF_TEST_WEBHOOK_SECRET` |
| `production` | `veriff-production-api-key` | `veriff-production-webhook-secret` | `VERIFF_API_KEY`, `VERIFF_PROD_API_KEY`, `VERIFF_WEBHOOK_SECRET`, `VERIFF_PROD_WEBHOOK_SECRET` |

- `gcp:deploy:issuance-gateway` always wires the active integration secret into `VERIFF_API_KEY`/`VERIFF_WEBHOOK_SECRET` and still exposes the legacy fallback env vars.
- Rotate secrets by adding versions: `printf 'uuid' | gcloud secrets versions add veriff-test-api-key --data-file=-`.
- Local development pulls from `.env` via SecretSpec; production uses Cloud Run + GCP Secret Manager with the same names.

## 6. Validation Checklist

- **Before deploy/build**
  - Run `devenv shell -- env:switch` and make sure the printed summary aligns with the environment you expect.
  - Run `devenv shell -- veriff:switch` and confirm the printed configuration matches intent.
  - Add new secret versions (`veriff-<integration>-api-key`, `veriff-<integration>-webhook-secret`).
  - Ensure Veriff Station integration points to the printed webhook URL and uses the matching signing secret.

- **After deploy/build**
  - Tail service logs: each backend will log `environment`, `cachet_env`, `veriff_integration`, and the resolved URLs at startup.
  - Hit `/health` (never `/healthz`) for Cloud Run services.
  - Run the mobile flow; issuance gateway logs will now show the integration in use plus polling/webhook activity.

Keeping the configuration in one JSON file, driving automation from it, and enforcing the naming/lifecycle conventions above keeps local, staging, and production aligned—and makes future integration switches a single scripted step instead of a day-long chase.
