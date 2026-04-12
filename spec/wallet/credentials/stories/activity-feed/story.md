---
story: activity-feed
domain: wallet/credentials
status: draft
last-reviewed: 2026-04-12
persona: returning-holder
---

# Activity Feed

**As a** returning-holder,
**I want to** see my verification history,
**so that** I know when and where my credentials were shared.

## Wireframes

- `activity-01-tab.svg` — Activity tab with verification history entries

## Acceptance Criteria

- **AC-1:** Activity tab shows a chronological list of verification events
- **AC-2:** Each entry shows: cachet name, verifier name, date/time, direction (shared/received)
- **AC-3:** Direction indicator distinguishes outgoing (shared) from incoming (received) verifications
- **AC-4:** Tapping an entry shows more detail (consent receipt)
- **AC-5:** Empty state shows appropriate message when no activity exists
- **AC-6:** Segmented control switches between "My Cachets" and "Activity" tabs

## Demo Scenarios

- **happy:** Pre-populated activity history

## Navigation

```
Home → Activity tab
       └── Tap entry → Activity detail / consent receipt
```
