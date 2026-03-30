---
name: Slice 6 decisions
description: DevEx & build — Dockerfiles over Nix containers, service list variable, fix script duplication and correctness
type: project
---

Aligned decisions from Slice 6 (DevEx & build reproducibility):

1. **Dockerfiles, not Nix containers** — remove the 90-line `containers` block from devenv.nix. Cloud Run uses Dockerfiles via `gcloud builds submit`.
2. Service list variable in devenv.nix — define once, generate all per-service scripts from it.
3. Fix `cd` chaining — all scripts use subshells `(cd ... && ...)` consistently.
4. Fix `lint:go` — remove `|| true`, run per-service properly.
5. Remove duplicate `yamllint` from packages.
6. Remove skeleton services from CI (per Slice 1).
7. CI cleanup: remove AndroidManifest debug step, fix coverage upload (add issuance-gateway), reconsider integration→build dependency.
8. Shorten enterShell banner to ~5 lines, add `dev:help` script for full listing.
9. Organize devenv.nix with clear section grouping.

**Why:** 40+ scripts with manual service enumeration, broken lint script, two redundant container build paths, CI running dead services.
**How to apply:** Service list variable is the foundation — do it first, then derive scripts. Container cleanup is independent.
