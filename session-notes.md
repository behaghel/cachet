# Session Notes

## Context
- Investigated Veriff 401 errors caused by the issuance gateway loading dummy credentials from `.env` instead of real values from `.env.local`.
- Confirmed default `dotenv` order and SecretSpec configuration inside `devenv`.

## Key Changes
- Moved real secrets into `.env`; renamed tracked dummy file to `.env.example` for sharing placeholders.
- Ensured `.env` remains gitignored and removed the tracked copy from the repo (`git rm --cached .env`).
- Updated `devenv.nix` to load only `.env` through `dotenv` and to export `SECRETSPEC_PROVIDER=dotenv://.env`.
- Wrapped the `dev:up` script with `secretspec run` so it always injects secrets.
- Adjusted AGENTS/CLAUDE/docs/FOUNDATION_AUDIT references to point at `.env` instead of `.env.local` and documented the new setup (including note that `.env` must never be committed).
- Added `.env.example` for dummy values and refreshed `.env.local.example` guidance.

## Current Status
- Services restarted with `devenv shell -- dev:up`, but issuance gateway logs stopped appearing (possibly due to process-compose TUI left open; terminal state corrupted).
- Latest mobile run returned HTTP 400 with no backend logs observed.

## Next Steps for Follow-up Session
1. Open a fresh terminal (or run `reset && stty sane && clear`) to recover from the TUI session.
2. Verify running processes via `devenv processes up` logs or `tail -f .devenv/processes.log`; avoid `devenv processes status` (not supported).
3. Confirm `VERIFF_API_KEY` value by executing inside `devenv shell`: `secretspec run -- printenv VERIFF_API_KEY`.
4. Reproduce the Veriff flow; capture precise HTTP 400 response body and any gateway logs.
5. If logs remain silent, inspect `/run/user/$UID/devenv-*/process-compose.log` or restart services with `devenv processes down` then `devenv shell -- dev:up`.

## Open Questions
- What endpoint is returning the 400 (gateway, ngrok tunnel, or external service)?
- Does the mobile app send requests to the expected local URL (`http://10.0.2.2:8090` / ngrok) with updated secrets?
- Are there additional environment variables missing (e.g., webhook URLs) after the `.env` changes?

## Validation Checklist (after restart)
- [ ] `curl -f http://localhost:8090/health` succeeds.
- [ ] Issuance gateway logs show the real UUID Veriff key during configuration.
- [ ] Mobile flow reaches `POST /sessions/veriff` with HTTP 200.
