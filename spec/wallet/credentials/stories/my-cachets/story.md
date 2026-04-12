---
story: my-cachets
domain: wallet/credentials
status: draft
last-reviewed: 2026-04-12
persona: returning-holder
---

# My Cachets

**As a** returning-holder,
**I want to** see all my cachets at a glance,
**so that** I know what trust I hold and can act on it.

## Wireframes

- `holder-04-vault-my-trust.svg` — My Cachets tab with credential cards
- `holder-05-empty-vault.svg` — Empty vault state with CTA to get first cachet

## Acceptance Criteria

- **AC-1:** My Cachets tab displays all stored credentials as cachet cards
- **AC-2:** Each card shows: cachet name, badge icon, trust status (verified/revoked/expired)
- **AC-3:** Tapping a card navigates to Cachet Detail
- **AC-4:** Empty state shows illustration + "Get your first cachet" CTA
- **AC-5:** FAB allows acquiring a new cachet (navigates to Pack Picker)
- **AC-6:** Segmented control switches between "My Cachets" and "Activity" tabs

## Demo Scenarios

- **happy:** Pre-populated vault with active credentials
- **empty:** Empty vault showing the empty state

## Navigation

```
Home → My Cachets tab (default)
       ├── Tap card → Cachet Detail
       ├── Tap FAB → Pack Picker (holder mode)
       └── Tap "Activity" → Activity tab
```
