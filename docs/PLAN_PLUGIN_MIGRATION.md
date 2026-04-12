# Plan: Migrate Cachet to Plugin-Driven Development

**Date:** 2026-04-12
**Context:** Cachet has grown organically with strong UX artifacts (20+ SVG wireframes, 13 screens, 5 demo scenarios, android-ux-review skill) but without a principled structure connecting user needs → specs → code. Four plugins have been created in the Veriff claude-plugins marketplace to enforce this structure. This plan migrates Cachet to use them.

**Plugins (all in `~/ws/veriff/claude-plugins/`, enabled in `devenv.nix`):**
- `spec-driven` — spec collection, verification, challenging (Veriff upstream)
- `spec-tdd` — iterative vertical-slice TDD (our plugin)
- `domain-tree` — DDD-informed domain tree with context map (our plugin)
- `ux-stories` — user-story-driven UX with BDD+TDD (our plugin)

**Testing the marketplace locally:** `known_marketplaces.json` points `installLocation` to `~/ws/veriff/claude-plugins/` (the local clone). Reset to the cache path before pushing.

---

## Phase 1: Domain Tree

**Goal:** Create `spec/domains.yaml` and the `spec/` directory scaffold.

**How:** Run `/domain-tree:init` and refine interactively.

**Expected domains (starting point — init will propose from codebase analysis):**

| Domain | Type | Code paths |
|--------|------|-----------|
| verification | core | `services/verifier/` |
| issuance | core | `services/issuance-gateway/` |
| registry | supporting | `services/registry/` |
| receipts | supporting | `services/receipts-log/` |
| relay | supporting | `services/relay/` |
| wallet | core | `mobile/shared/`, `mobile/androidApp/` |
| common | shared-kernel | `services/common/` |
| security | core | `services/common/crypto/`, threat model |
| ux | supporting | `mobile/androidApp/.../ui/theme/`, `.../ui/components/` |
| infra | generic | `deploy/`, Cloud Run config |
| cicd | generic | `.github/workflows/`, `scripts/` |

**Expected context map relationships:**
- issuance → wallet: open-host-service (OpenID4VCI)
- wallet → verification: anti-corruption-layer
- verification → registry: customer-supplier (GET /policy/manifest)
- issuance → common: shared-kernel (credential types)
- verification → common: shared-kernel (credential types)
- receipts → verification: customer-supplier (consent events)

**Wallet subdomains to consider:**
- wallet/onboarding
- wallet/credentials
- wallet/verification-flow

**Deliverables:**
- [ ] `spec/domains.yaml` with classifications and context map
- [ ] `spec/` directory tree with skeleton `index.md` per domain
- [ ] Validate with `/domain-tree:check`

---

## Phase 2: Personas

**Goal:** Create `spec/personas.md` defining who uses Cachet.

**Personas to capture (already implicit in code and wireframes):**

| Persona | Description | Source |
|---------|-------------|--------|
| first-time-user | New to Cachet, no credentials, unfamiliar with trust verification | Onboarding wireframes, empty vault scenario |
| returning-holder | Has credentials, uses app regularly to share trust | Happy path demo scenario, vault wireframes |
| verifier | Business user who needs to verify someone's credentials | Verifier wireframes (verify-01, verify-02) |
| revoked-holder | Had credentials but one or more were revoked | Revoked demo scenario, SPEC_REVOKED_CACHET_UX.md |

**Deliverables:**
- [ ] `spec/personas.md`

---

## Phase 3: Retrospective User Stories

**Goal:** Write user stories for existing screens, grouping wireframes by story.

**Stories to write (derived from existing wireframes and demo scenarios):**

| Story | Domain | Wireframes | Demo Scenario |
|-------|--------|-----------|--------------|
| first-launch | wallet/onboarding | holder-01 through holder-04 (4 onboarding screens) | — (no demo_mode) |
| my-cachets | wallet/credentials | holder-04-vault-my-trust, holder-05-empty-vault | happy, empty |
| activity-feed | wallet/credentials | activity-01-tab | happy |
| cachet-detail | wallet/credentials | cachet-01-detail, cachet-01-detail-hardware | happy |
| revoked-cachet | wallet/credentials | cachet-01-detail-revoked, holder-04-vault-revoked | revoked |
| get-new-cachet | wallet/verification-flow | holder-06-pick-pack | happy |
| scan-to-verify | wallet/verification-flow | cachet-02-qr-scan, cachet-03-incoming-request | happy |
| verification-result | wallet/verification-flow | cachet-04-result-pass, cachet-04-result-pass-age, cachet-05-result-fail, cachet-05-result-fail-seller | happy, seller-only |
| verifier-request | verification | verify-01-new-request, verify-02-show-qr | — |

**For each story, use `/ux-stories:write`:**
1. Write the story (persona, goal, acceptance criteria)
2. Move the relevant wireframes from `design/wireframes/` into the story's `wireframes/` directory
3. Keep `design/wireframes/MANIFEST.md` updated (or replace it with the domain-tree structure)

**Order:** Start with `first-launch` (simplest, self-contained) to validate the workflow before tackling more complex stories.

**Deliverables:**
- [ ] 9 story directories under `spec/{domain}/stories/`
- [ ] Wireframes relocated from `design/wireframes/` to story directories
- [ ] `design/wireframes/MANIFEST.md` updated or deprecated

---

## Phase 4: BDD Scenarios

**Goal:** Write Gherkin scenarios for each story, starting from the 5 existing demo scenarios.

**For each story, use `/ux-stories:scenarios`:**
1. Read the story + wireframes
2. Generate Gherkin scenarios from acceptance criteria
3. Map scenarios to demo scenarios (happy, revoked, expired, empty, seller-only)

**Priority order (by demo scenario coverage):**
1. `my-cachets` — happy + empty demo scenarios provide test data
2. `revoked-cachet` — revoked demo scenario, existing SPEC_REVOKED_CACHET_UX.md
3. `verification-result` — happy + seller-only demo scenarios
4. `first-launch` — no demo scenario needed (onboarding is pre-auth)
5. Remaining stories

**Deliverables:**
- [ ] `scenarios.feature` in each story directory
- [ ] Every AC mapped to at least one scenario
- [ ] Every wireframe referenced by at least one scenario

---

## Phase 5: BDD Tooling

**Goal:** Make `.feature` files executable against the Android app.

**Options to evaluate:**

| Framework | Pros | Cons |
|-----------|------|------|
| Cucumber + Espresso | Industry standard Gherkin runner, rich step definition model | Heavyweight setup, slow test execution |
| Cucumber + Compose Testing | Native Compose matchers, faster than Espresso | Less mature, fewer examples |
| Maestro | YAML-based flows, fast, good for mobile | Not Gherkin-native, separate language |

**Recommended approach:** Start with **Cucumber + Compose Testing**:
- `.feature` files are already Gherkin (from Phase 4)
- Step definitions use Compose test rules and semantic matchers
- Demo scenarios provide test data fixtures
- The android-ux-review skill handles visual verification separately

**Setup tasks:**
- [ ] Add Cucumber dependencies to `mobile/androidApp/build.gradle.kts`
- [ ] Create step definition base class with demo scenario injection
- [ ] Write step definitions for the first story (first-launch)
- [ ] Verify the pipeline: `.feature` → step definitions → Compose tests → pass/fail
- [ ] Add `devenv shell -- android:bdd` script

**Deliverables:**
- [ ] BDD test runner configured
- [ ] Step definitions for at least 2 stories
- [ ] CI integration (optional, can defer)

---

## Phase 6: Spec Collection for Core Domains

**Goal:** Write behavioral specs for core domains using `spec-driven`.

**This phase runs in parallel with Phases 3-5 for non-UX domains.**

For each core domain without UX (verification, issuance, security):
1. Use `/spec-driven:collect-spec` to collect behavioral specs
2. Run `spec-challenger` to find gaps
3. Save specs in `spec/{domain}/`

**Priority:** verification (most complex, most critical) → issuance → security

**Deliverables:**
- [ ] Behavioral specs for core domains
- [ ] Challenger reports reviewed

---

## Phase 7: Going Forward

**Goal:** Establish the new workflow as the default.

**The new workflow for UX changes:**
```
/ux-stories:write → wireframes → /ux-stories:scenarios → spec-challenger → /ux-stories:deliver
```

**The new workflow for backend changes:**
```
/spec-driven:collect-spec → spec-challenger → /spec-tdd:plan → /spec-tdd:iterate
```

**Enforcement:**
- `story-guardian` agent watches for UX code changes without stories
- `boundary-enforcer` agent watches for domain boundary violations
- `tdd-coach` agent watches for TDD discipline violations
- Spec-on-touch convention: first modification to any domain requires a spec

**Process rules:**
- No UX code change without a user story in `spec/{domain}/stories/`
- No wireframe modification to match implementation (fix implementation instead)
- No BDD scenario without a wireframe reference
- Core domains require spec approval before implementation
- Shared kernel changes require notification of all consumers

---

## Migration checklist

- [ ] Phase 1: `spec/domains.yaml` + directory scaffold
- [ ] Phase 2: `spec/personas.md`
- [ ] Phase 3: 9 retrospective user stories with wireframes relocated
- [ ] Phase 4: Gherkin scenarios for all stories
- [ ] Phase 5: BDD tooling running for at least 2 stories
- [ ] Phase 6: Behavioral specs for verification + issuance
- [ ] Phase 7: Workflow documented, agents active, team aligned

---

## Notes

- The marketplace is currently pointed at the local clone (`~/ws/veriff/claude-plugins/`). Reset `known_marketplaces.json` installLocation to the cache path before pushing plugins to GitHub.
- The `~/.codex/skills/spec-driven-tdd/` skill can be retired after this migration — its functionality is now split between `spec-tdd` and `ux-stories` plugins.
- The existing `android-ux-review` skill in `.claude/skills/` complements `ux-stories` — it handles visual verification (screenshot vs wireframe comparison) which is step 2d of `/ux-stories:deliver`.
