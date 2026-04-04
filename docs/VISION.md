# Product Vision -- Cachet

## The problem

Proving you're trustworthy online means handing over personal data to every party that asks. A parent community checking a caregiver gets their birthdate, ID number, criminal record details, referees' names. The verifier becomes a data controller. The person being verified loses control of their information. The system is backwards.

## The inversion

Cachet lets you prove things about yourself without revealing the underlying facts:

- Not "my birthdate is March 15, 1990" but **"I am over 18"**
- Not "here are my three referees" but **"I have 2+ verified references"**
- Not "here is my criminal record" but **"my background check is clean"**

Cryptographic proofs of predicates, bundled into reusable **Trust Packs** for specific contexts. The person being verified stays in control. The verifier gets exactly the assurance they need -- nothing more.

## A customer story

You run a parent community in Madrid. New caregivers join weekly; you want them onboarded fast without risking safety or hoovering up personal data you don't want to store. A candidate applies. You click Request Pack -> Childcare-Readiness (ES). They open the link on their phone, unlock their Cachet wallet, and in under two minutes present privacy-preserving proofs: age >= 18, recent liveness + ID, clean childcare-specific background check, >=2 verified references. You don't see their birthdate, ID number, or referees' names -- only a badge: "Childcare-Ready (ES) -- valid 90 days" with an explainability pane that shows what was proven, by whom, and when. A Consent Receipt is logged (hash only) so you can audit later. You onboard with confidence, in minutes, without becoming a data controller for sensitive PII.

That's Cachet: portable trust, human rights intact.

## High-level architecture

- **Holder edge** (Cachet Wallet, iOS/Android) -- hardware-backed keys, passkeys sign-in, credential vault, proof planner, explainability UI, consent receipts. Dual mode: credential holder AND relying party. All PII lives here.
- **Verifier** -- deterministic policy engine that validates presentations against signed Pack definitions (no ML gating). Returns a Badge and human-readable "why".
- **Issuance Gateway** -- converts Veriff outcomes and partner attestations into verifiable credentials.
- **Registries** -- signed, versioned catalogs for Packs/Policies and Issuers/Schemas/Revocation endpoints.
- **Receipts & Transparency Log** -- creates consent receipts client-side; anchors salted hashes in an append-only Merkle log with Signed Tree Heads for public auditability.
- **Connector Hub & Vouching** -- pulls platform stats to mint credentials; runs reference capture -> ZK "count >= K" proofs.

Design intent: edge-first privacy, standards for interop, and explainability by default.

## Key technologies

| Technology | Why it matters |
|-----------|---------------|
| W3C Verifiable Credentials 2.0 | Standard envelope for portable claims; interop with wallets and ecosystems |
| SD-JWT VC | Selective disclosure (prove age >= 18 without revealing birthdate) |
| BBS+ signatures | Unlinkable selective disclosure across multiple presentations |
| OpenID4VCI / OpenID4VP | Issuance and presentation protocols via standard OIDC-style flows |
| DIDs | Stable identifiers for issuers/RPs/wallets with key rotation |
| StatusList2021 | Scalable revocation lists with privacy |
| ZK-SNARKs (Plonk/Halo2) | Privacy-preserving predicates over aggregates (e.g., references >= 2) |
| Transparency Log (Merkle + STH) | Tamper-evident public auditing of receipt hashes without exposing PII |

## Core concepts

- **Trust Pack** -- a named, reusable set of predicates for a purpose (e.g., Childcare-Readiness, Safe Seller).
- **Predicate** -- a property proven, not the raw attribute (e.g., age >= 18, fulfilment >= 95%).
- **Badge** -- contextual outcome, time-boxed (e.g., Childcare-Ready (ES, 90d)). No global scores.
- **Issuer** -- entity that attests claims (e.g., Veriff for ID+liveness; justice ministry for records).
- **Holder** -- person being assessed, controlling what to disclose. Can also act as RP via mobile-to-mobile verification.
- **Relying Party (RP)** -- assessor requesting a Pack (parent, buyer, marketplace, or another Cachet user).
- **Presentation** -- bundle of proofs (SD-JWT/BBS+/ZK) satisfying Pack predicates.
- **Consent Receipt** -- signed record of purpose and predicates proven; hash anchored to the transparency log.
- **Policy Manifest** -- signed "constitution" that defines guardrails, crypto suites, fairness rules.

## Key flows

### Request Pack and Present (Standard RP -> Holder)

1. RP initiates Request Pack (e.g., Childcare-Readiness (ES)) -> QR/deeplink.
2. Wallet fetches Pack and Policy, plans proofs locally, and presents.
3. Verifier checks signatures, revocation, freshness, jurisdiction -> returns Badge + explainability.
4. Wallet issues Consent Receipt; hash anchored to transparency log; RP stores minimal copy.

### Mobile-to-Mobile Verification (Holder -> Holder)

1. User A opens Cachet app -> "Request Verification", selects Trust Pack.
2. App generates QR code/deeplink with verification request.
3. User B scans QR/opens link -> wallet shows consent screen.
4. User B approves -> presents credentials to prove Pack predicates.
5. User A receives push notification with Badge result + explainability.
6. Both users get Consent Receipts; hashes anchored to transparency log.

### Issuance (foundational VC)

1. Holder completes Veriff flow (ID + liveness).
2. Issuance Gateway mints SD-JWT VC; adds to wallet; sets revocation handle.

### Vouching (references >= K)

1. Candidate invites referees -> lightweight attest + identity check.
2. Vouching Service issues set-membership credential; wallet proves count >= K via ZK.

### Appeal and revocation

1. Issuer updates StatusList (soft-disable window for appeals).
2. Holder appeals; oversight may re-enable pending review.
