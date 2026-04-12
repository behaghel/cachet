# Plan: Test Plugin Changes on PR #102

**Date:** 2026-04-12
**Context:** The ux-stories and domain-tree plugins were updated with learnings from the first migration. The Cachet PR #102 was built with the OLD plugin behavior and needs to be brought into conformance. This plan tests the new plugin behavior by running the plugins against the existing artifacts.

**What changed in the plugins:**
1. `.feature` IS the story — no separate `story.md`
2. `@wireframe:` tags replace copied SVGs and Visual Match scenarios
3. `Scenario Outline` mandatory for data variants
4. `index.md` must not duplicate `domains.yaml`
5. DRY: no cross-story scenario duplication

---

## Test 1: `/domain-tree:check` catches index.md duplication

**Goal:** Verify the check command flags that existing `index.md` files duplicate `domains.yaml`.

**Steps:**
1. Run `/domain-tree:check`
2. Expected: report flags description/context-map duplication in index.md files
3. Fix: rewrite index.md files to only contain ubiquitous language, invariants, concepts
4. Verify: re-run `/domain-tree:check` — duplication warnings gone

---

## Test 2: `/ux-stories:write` produces .feature, not story.md

**Goal:** Verify the write command creates a `.feature` file as the single story artifact.

**Steps:**
1. Delete an existing story.md + scenarios.feature pair (e.g., `spec/wallet/onboarding/stories/first-launch/`)
2. Run `/ux-stories:write first-launch`
3. Expected: creates only `scenarios.feature` with Feature header containing persona/goal/context
4. Expected: does NOT create `story.md`
5. Expected: wireframes referenced via `@wireframe:` tags, not copied into story dir

---

## Test 3: `/ux-stories:scenarios` enforces DRY and Scenario Outlines

**Goal:** Verify the scenarios command collapses data variants into Outlines and removes Visual Match boilerplate.

**Steps:**
1. Run `/ux-stories:scenarios spec/wallet/onboarding/stories/first-launch/`
2. Expected: onboarding screens 1-4 use a single `Scenario Outline` with Examples table
3. Expected: no "Visual match" scenarios generated
4. Expected: wireframe references are `@wireframe:` tags, not comments
5. Repeat for `spec/wallet/verification-flow/stories/verification-result/` — verify pass/fail variants use Outline

---

## Test 4: Migrate existing 9 stories to new format

**Goal:** Apply the new plugin conventions to all existing stories.

**For each of the 9 stories:**
1. Delete `story.md`
2. Delete copied wireframe SVGs from `wireframes/` subdirectories
3. Rewrite `scenarios.feature`:
   - Add Feature header with persona, goal, context, out of scope (from old story.md)
   - Add `@story:`, `@domain:`, `@priority:`, `@status:` tags to Feature
   - Replace `# Wireframe:` comments with `@wireframe:` tags on each scenario
   - Collapse data variants into `Scenario Outline` with Examples
   - Remove all "Visual match" boilerplate scenarios
   - Remove cross-story duplicates (tab switching belongs in one story only)
4. Remove empty `wireframes/` directories

**Stories to migrate:**
- [ ] first-launch (wallet/onboarding)
- [ ] my-cachets (wallet/credentials)
- [ ] activity-feed (wallet/credentials)
- [ ] cachet-detail (wallet/credentials)
- [ ] revoked-cachet (wallet/credentials)
- [ ] get-new-cachet (wallet/verification-flow)
- [ ] scan-to-verify (wallet/verification-flow)
- [ ] verification-result (wallet/verification-flow)
- [ ] verifier-request (verification)

---

## Test 5: Migrate index.md files to new format

**Goal:** Remove duplication between `domains.yaml` and `index.md`.

**For each of the 14 index.md files:**
1. Remove description text (it's in domains.yaml)
2. Remove context map relationships section (it's in domains.yaml)
3. Remove `type` from frontmatter (it's in domains.yaml)
4. Remove `consumers` from frontmatter (it's in domains.yaml)
5. Add reference line: `> See spec/domains.yaml for description, classification, code paths, and context map.`
6. Keep ONLY: ubiquitous language, invariants, key concepts, domain events

---

## Test 6: Validate spec.md files against OpenAPI

**Goal:** Verify that backend domain specs (verification, issuance) don't duplicate what OpenAPI already expresses.

**Steps:**
1. Compare `spec/verification/spec.md` endpoints with `schemas/openapi.yaml`
2. Identify what spec.md adds over OpenAPI (invariants, threat mitigations, algorithms)
3. Decide: keep spec.md for non-API behavioral specs, reference OpenAPI for endpoint contracts
4. This is a design decision — present findings to user before acting

---

## Test 7: `/domain-tree:check` passes after all migrations

**Goal:** Full health check after all changes.

**Steps:**
1. Run `/domain-tree:check`
2. Expected: all checks pass, no duplication warnings, no orphaned code
3. Verify: every wireframe in `design/wireframes/` is referenced by at least one `@wireframe:` tag

---

## Execution order

1. Test 5 (index.md) — independent, can go first
2. Test 4 (story migration) — bulk of the work
3. Test 1 (domain-tree:check) — validates Test 5
4. Test 3 (scenarios DRY) — validates Test 4
5. Test 7 (full check) — validates everything
6. Test 6 (OpenAPI) — design decision, discuss with user
7. Test 2 (write command) — validates plugin behavior for future stories

Tests 2 and 6 require user input. Tests 1, 3, 5, 7 can be automated. Test 4 is the main migration work.
