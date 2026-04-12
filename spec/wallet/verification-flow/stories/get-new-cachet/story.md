---
story: get-new-cachet
domain: wallet/verification-flow
status: draft
last-reviewed: 2026-04-12
persona: first-time-user
---

# Get New Cachet

**As a** first-time-user (or returning-holder),
**I want to** browse available Trust Packs and start the credential acquisition flow,
**so that** I can earn a new cachet.

## Wireframes

- `holder-06-pick-pack.svg` — Pack picker in holder mode (choose which cachet to earn)

## Acceptance Criteria

- **AC-1:** Pack picker shows all available Trust Packs from the registry
- **AC-2:** Each pack card shows: pack name, description, required verification type
- **AC-3:** Tapping a pack initiates the credential acquisition flow (Veriff session or demo consent)
- **AC-4:** User can cancel and return to vault
- **AC-5:** Pack picker is reachable from empty vault CTA and from FAB on My Cachets tab

## Demo Scenarios

- **happy:** Pack list with Childcare, Age, Identity, Seller packs available

## Navigation

```
My Cachets → FAB → Pack Picker (holder mode) → Select pack → Acquisition flow
Empty Vault → "Get your first cachet" → Pack Picker (holder mode)
```
