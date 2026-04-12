---
story: first-launch
domain: wallet/onboarding
status: draft
last-reviewed: 2026-04-12
persona: first-time-user
---

# First Launch

**As a** first-time-user,
**I want to** understand what Cachet does and how it empowers me,
**so that** I feel confident to start using the app.

## Wireframes

- `holder-01-onboarding-1.svg` — "Don't take their word for it" (demand trust)
- `holder-02-onboarding-2.svg` — "Prove yourself without over-sharing"
- `holder-03-onboarding-3.svg` — "Your trust, your rules"
- `holder-04-onboarding-4.svg` — "Get started" / transition to vault

## Acceptance Criteria

- **AC-1:** App launches to onboarding screen 1 when no credentials exist and onboarding has not been completed
- **AC-2:** User can swipe or tap "Next" to advance through 4 onboarding screens
- **AC-3:** Each screen conveys a distinct value proposition (demand trust → prove yourself → your rules → get started)
- **AC-4:** After completing onboarding, user arrives at the empty vault screen
- **AC-5:** Onboarding is only shown once — subsequent launches skip to vault
- **AC-6:** Step indicator shows progress (1/4, 2/4, etc.)

## Demo Scenario

None — onboarding is tested by launching without `demo_mode`.

## Navigation

```
App launch (fresh install) → Onboarding 1 → 2 → 3 → 4 → Empty Vault
```
