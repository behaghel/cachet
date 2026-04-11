# Revoked Cachet UX Spec

**Issues:** #59, #85
**Date:** 2026-04-11

## Context

When a cachet is revoked, the app must make the revoked state immediately obvious and prevent sharing. This spec captures the visual and behavioural requirements for both the detail screen and the vault grid.

## Cachet Detail Screen (revoked state)

Wireframe: `design/wireframes/cachet-01-detail-revoked.svg`

### Hero section
- **Forbidden sign overlay**: a translucent red circle-with-slash overlays the shield badge, visually marking it as invalid. Same treatment as the verification-fail result screen.
- **Status chip**: red "Revoked" chip (already implemented).
- **Share button**: disabled/greyed out with explanatory text "Revoked cachets cannot be shared" (already implemented).

### Metadata rows
- **"Expires" field replaced**: instead of showing the expiration date, display **"Revoked"** label in red with the revocation date (e.g., "Apr 5, 2026"). This replaces the "Expires" label+value entirely.
- **Date alignment fix**: "Issued" and "Expires"/"Revoked" rows must use a consistent two-column grid so labels and values align horizontally. Both date values must be single-line (no wrapping).
- **"Revoke this cachet" link**: removed entirely (already revoked, nothing to act on).

### Predicates and activity
- Predicates section remains unchanged (shows what the cachet *used to* prove).
- Related activity section remains unchanged (shows historical shares + the revocation event).

## My Cachets Vault (sort order)

Wireframe: `design/wireframes/holder-04-vault-revoked.svg`

### Sort order
Revoked cachets appear **last** in the grid, after all active cachets. Within the revoked group, most recently revoked cachets appear first.

**Order:** active (most recent first) > revoked (most recently revoked first)

### Visual treatment
- Red border on card (already in wireframe).
- "Revoked" chip instead of "Active" chip (already in wireframe).
- "Revoked on {date}" at bottom of card (already in wireframe).

## Wireframes

| Screen | File | Changes |
|--------|------|---------|
| Detail (revoked) | `cachet-01-detail-revoked.svg` | Add forbidden overlay on shield; replace "Expires" with "Revoked" date in red; remove "Revoke this cachet" link; fix date row alignment |
| Vault (revoked) | `holder-04-vault-revoked.svg` | Revoked cards sort last (already shown correctly in wireframe) |
