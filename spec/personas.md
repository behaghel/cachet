---
status: draft
last-reviewed: 2026-04-12
---

# Personas

Who uses Cachet — derived from wireframes, demo scenarios, and architecture docs.

## first-time-user

**Role:** A person who just installed Cachet and has no credentials yet.

**Goals:**
- Understand what Cachet does (demand trust from others, prove yourself without over-sharing)
- Complete onboarding and acquire their first credential
- Feel empowered, not surveilled

**Context:**
- Arrives via app store or a verifier's invitation link
- Has never held a verifiable credential
- May not understand terms like "credential" or "verification"

**Key screens:** Onboarding 1-4, Empty Vault, Pick Pack

**Demo scenario:** _(none — onboarding is pre-demo)_

---

## returning-holder

**Role:** A person who already holds one or more credentials and uses Cachet regularly.

**Goals:**
- View their cachets at a glance (vault / My Cachets tab)
- Share credentials when requested by a verifier
- Track verification history (Activity tab)
- Acquire new cachets as needed

**Context:**
- Has completed onboarding
- Has at least one active credential in the vault
- Understands the scan-consent-share flow

**Key screens:** My Cachets, Activity, Cachet Detail, QR Scanner, Incoming Request, Verification Result

**Demo scenarios:** happy, seller-only

---

## verifier

**Role:** A business or individual who needs to verify someone's credentials.

**Goals:**
- Select which Trust Pack to verify against
- Generate a QR code for the holder to scan
- Receive verification results (pass/fail with predicates)
- Trust results without seeing raw personal data

**Context:**
- Uses Cachet from the "verifier" perspective (same app, different flow)
- Initiates verification by choosing a pack and showing a QR
- Never sees the holder's actual data — only predicate results

**Key screens:** New Request (pick pack), Show QR (wait), Verification Result

**Demo scenario:** happy (verifier path)

---

## revoked-holder

**Role:** A person who had valid credentials but one or more were revoked.

**Goals:**
- Understand why a credential was revoked
- Know which cachets are still valid
- Take action to re-acquire revoked credentials

**Context:**
- Was a returning-holder, but issuer revoked a credential (e.g., expired background check)
- Sees visual distinction between active and revoked cachets
- Needs clear next steps, not just a red badge

**Key screens:** Vault with Revoked Card, Revoked Detail

**Demo scenario:** revoked

---

## Notes

- **first-time-user** and **returning-holder** are the same person at different lifecycle stages
- **verifier** may be the same person using the app's verifier mode, or a separate business user
- **revoked-holder** is a returning-holder who hit a lifecycle edge case — the persona exists to ensure revocation UX is designed with empathy, not just error handling
- A fifth persona (**expired-holder**) may emerge when credential expiry is implemented
