# Product Brief — Cachet v0.5

## Intro — a customer story (Relying Party)

You run a parent community in Madrid. New caregivers join weekly; you want them onboarded fast without risking safety or hoovering up personal data you don’t want to store. A candidate applies. You click Request Pack → Childcare‑Readiness (ES). They open the link on their phone, unlock their Cachet wallet, and in under two minutes present privacy‑preserving proofs: age ≥ 18, recent liveness + ID, clean childcare‑specific background check, ≥2 verified references. You don’t see their birthdate, ID number, or referees’ names — only a badge: “Childcare‑Ready (ES) — valid 90 days” with an explainability pane that shows what was proven, by whom, and when. A Consent Receipt is logged (hash only) so you can audit later. You onboard with confidence, in minutes, without becoming a data controller for sensitive PII.

That’s Cachet: portable trust, human rights intact.

## High‑level architecture (overview)

- Holder edge (Cachet Wallet, iOS/Android) — hardware‑backed keys, passkeys sign‑in, credential vault, proof planner, explainability UI, consent receipts. **Dual mode**: credential holder (receiving/presenting) AND relying party (requesting verification via QR/deeplink generation). All PII lives here.
- Verifier — deterministic policy engine that validates presentations against signed Pack definitions (no ML gating). Returns a Badge and human‑readable “why”.
- Issuance Gateway — converts Veriff outcomes and partner attestations (justice ministries, platforms) into verifiable credentials.
- Registries — signed, versioned catalogs for Packs/Policies and Issuers/Schemas/Revocation endpoints.
- Receipts & Transparency Log — creates consent receipts client‑side; anchors salted hashes in an append‑only Merkle log with Signed Tree Heads for public auditability.
- Connector Hub & Vouching — pulls platform stats to mint credentials; runs reference capture → ZK “count ≥ K” proofs.
- Design intent: edge‑first privacy, standards for interop, and explainability by default.

## Key technologies (why they matter)

- W3C Verifiable Credentials 2.0 — standard envelope for portable claims; interop with wallets & ecosystems.
- SD‑JWT VC — selective disclosure of attributes (prove age ≥ 18 without birthdate).
- BBS+ signatures — unlinkable selective disclosure across multiple presentations.
- OpenID4VCI / OpenID4VP — issuance & presentation protocols so RPs can request Packs via standard OIDC‑style flows.
- DIDs (Decentralized Identifiers) — stable identifiers for issuers/RPs/wallets with key rotation.
- StatusList2021 revocation — scalable revocation lists with privacy; verifier checks freshness/validity.
- ZK‑SNARKs (Plonk/Halo2) — privacy‑preserving predicates over aggregates (e.g., references ≥ 2, fulfilment ≥ 95%).
- C2PA — content provenance for photos/videos submitted during assessments (helpful vs. deepfakes).
- Trusted Execution Environments (SGX/SEV‑SNP) — optional server‑side secure compute for sensitive transforms.
- HSM‑backed signing — protects registry/issuance/log signing keys; auditable key ceremonies.
- Passkeys + Secure Enclave/StrongBox — phishing‑resistant auth and hardware key storage on devices.
- WebAssembly proof planner — portable, sandboxed local planner that composes the cheapest valid proofs per Pack.
- Transparency Log (Merkle + STH) — tamper‑evident public auditing of receipt hashes without exposing PII.

## Core concepts

- Trust Pack — a named, reusable set of predicates for a purpose (e.g., Childcare‑Readiness, Safe Seller).
- Predicate — a property proven, not the raw attribute (e.g., age ≥ 18, fulfilment ≥ 95%).
- Badge — contextual outcome, time‑boxed (e.g., Childcare‑Ready (ES, 90d)). No global scores.
- Issuer — entity that attests claims (e.g., Veriff for ID+liveness; justice ministry for records).
- Holder — person being assessed, controlling what to disclose. **Can also act as RP** when requesting verification from others via mobile app.
- Relying Party (RP) — assessor requesting a Pack (parent, buyer, marketplace, **or another Cachet user** via mobile-to-mobile verification).
- Presentation — bundle of proofs (SD‑JWT/BBS+/ZK) satisfying Pack predicates.
- Consent Receipt — signed record of purpose and predicates proven; hash anchored to the transparency log.
- Policy Manifest — signed “constitution” that defines guardrails, crypto suites, fairness rules.
- Trust Contacts & Vouches — private graph of endorsements with decay and context tags.

## Metrics that matter

### Reliability & speed

- Time‑to‑trust (TTT): request → badge (p50/p95).
- Verification success rate: % requests resulting in badge without re‑try.
- Issuer SLOs: availability/latency of revocation and issuance endpoints.
- Anchoring latency: time to include receipt hash in transparency log.

### Quality & safety

- Appeal rate and resolution time; false‑positive/negative analysis.
- Consent comprehension: % holders who confirm they understood what
  was proven/shared.
- Privacy budget: % presentations at predicate‑only (no raw attributes).

### Adoption & growth

- Pack reuse rate: recipients who reuse their Pack within 30/90 days.
- RP conversion: legacy → native OID4VP.
- Virality coefficient: invites generated per completed assessment.

## Key flows (happy paths)

### Request Pack & Present (Standard RP → Holder)

1. RP initiates Request Pack (e.g., Childcare‑Readiness (ES)) →
   QR/deeplink.
2. Wallet fetches Pack & Policy, plans proofs locally, and presents.
3. Verifier checks signatures, revocation, freshness, jurisdiction →
   returns Badge + explainability.
4. Wallet issues Consent Receipt; hash anchored to transparency log;
   RP stores minimal copy.

### Mobile-to-Mobile Verification (Holder → Holder)

1. **User A** (requesting verification) opens Cachet app → "Request Verification" 
2. Selects Trust Pack (e.g., "Age Verification", "Safe Seller")
3. App generates **QR code/deeplink** with verification request
4. **User B** scans QR/opens link → wallet shows consent screen
5. User B approves → presents credentials to prove Pack predicates
6. **User A receives push notification** with Badge result + explainability
7. Both users get Consent Receipts; hashes anchored to transparency log

**Use cases**: 
- Parent verifying caregiver credentials peer-to-peer
- Marketplace buyer verifying seller trust score  
- Event organizer checking age verification
- Community member validating background clearance

### Issuance (foundational VC)

1. Holder completes Veriff flow (ID + liveness).
2. Issuance Gateway mints SD‑JWT VC; adds to wallet; sets revocation handle.

### Vouching (references ≥ K)

1. Candidate invites referees → lightweight attest + identity check.
2. Vouching Service issues set‑membership credential; wallet proves count ≥ K via ZK.

### Platform stats → credential

1. Holder links marketplace account.
2. Connector Hub ingests signed stats; issues platform tenure / fulfilment VCs.

## Appeal & revocation

1. Issuer updates StatusList (soft‑disable window for appeals).
2. Holder appeals; oversight may re‑enable pending review.

# Legacy fallback

1. For RPs without OID4VP: one‑time signed summary PDF + verify link;
   nudges toward native support.

## Build phases & exit criteria

### Phase A — MVP (0–3 months)

- Wallet core (keys/passkeys, vault), two Packs (Childcare, Safe Seller), Verifier with deterministic policy, Issuance via Veriff, Consent Receipts + basic Transparency Log, RP SDKs (web/mobile).

- Exit: p95 TTT < 2m; ≥ 85% predicate‑only presentations; 2 live RP pilots.

### Phase B — Proof depth (3–6 months)

- Vouching ZK flow, platform connectors (≥2), device attestation predicate, BBS+ for unlinkability, jurisdictional Pack variants (FR/EE/ES).

- Exit: Pack reuse rate ≥ 35%; issuer SLOs ≥ 99.9%/30d; appeals resolved median < 5d.

### Phase C — Scale & governance (6–9 months)

- Trust Contacts, Policy Studio, oversight council, full transparency reporting, TEEs for sensitive transforms.

- Exit: ≥ 10 enterprise RPs; transparency log monitored by ≥ 2 independent auditors.

## Business model

**Who pays?** Primarily **Relying Parties (RPs)**— they capture the value (reduced fraud, faster conversion, simpler compliance). Holders get a generous free tier.

### Pricing components

- **Per‑verification fee** (per successful Pack presentation) with volume tiers.
- **Premium Packs** (domain‑specific content, e.g., childcare, fintech
  seller, licensed contractor) priced higher due to issuer costs.
- **Enterprise plan** for marketplaces/community platforms: SLA, custom
  Pack variants, analytics, dedicated support.
- **Issuer marketplace rev‑share** where applicable (e.g., background
  check partners).

### What’s off‑limits

- **No data brokerage**. No selling or renting PII. Revenue is from
  verification value‑add, not data.

### Adoption levers

- Open SDKs & sample code; legacy fallback (signed summary) to ease integration.
- Virality loop: every assessment invites the counterparty to mint a reusable Pack.
- Compliance lift: purpose‑binding, audit receipts, and explainability reduce RP legal risk and storage burden.

### Cost drivers

- Issuer integrations & revocation lookups; ZK proof verification;
  HSM/TEE ops; compliance/oversight.

### Moat

- Creator‑anchored brand & Policy Manifest transparency, issuer
  network density, and Pack library quality.

# Cachet — Trust Packs (Localized), Policy Manifest & Product Brief v0.4

_Last updated: 31 Aug 2025_

---

### 10) Build phases

1. **Phase A**: Registry, Wallet core, Issuance (Veriff), Verifier, two Packs, Receipts + Log.
2. **Phase B**: Vouching ZK, Connector Hub (1–2 platforms), device attestation, SDK hardening.
3. **Phase C**: Trust Contacts, Policy Studio, multi‑region packs, oversight operations.

---

## Transparency Log Design (Consent Receipts)

To strengthen accountability and prevent silent tampering, every **Consent Receipt** hash is anchored into an append‑only, publicly auditable log. This log provides external parties, oversight bodies, and users with verifiable guarantees that receipts were neither fabricated nor deleted.

### Design Principles

1. **Append‑only** — no deletion or overwriting; new entries only.
2. **Publicly auditable** — anyone can fetch log checkpoints and verify inclusion.
3. **Privacy‑preserving** — only salted hashes of receipts are logged; no personal data.
4. **Interoperable** — built on transparent log protocols inspired by Certificate Transparency (RFC 6962).
5. **Tamper‑evident** — Merkle trees ensure each checkpoint commits to all prior entries.

---

### Architecture

**Components:**

- **Receipt Issuers** (Cachet app or server): emit Consent Receipts and compute salted hash.
- **Transparency Log Service**: accepts hashed entries, maintains append‑only Merkle tree.
- **Auditors/Monitors**: independent entities that fetch, store, and verify log checkpoints.
- **Oversight Council**: reviews reports, manages dispute processes.

**Flow:**

1. Consent Receipt generated by holder.
2. SHA‑256 hash + salt → Transparency Log.
3. Log assigns sequential index, updates Merkle root.
4. Log issues **Signed Tree Head (STH)** — contains size, root hash, timestamp, signature.
5. Holder app stores proof of inclusion (Merkle path + STH).
6. Auditors poll logs, verify consistency proofs, detect misbehavior.

---

### Data Model — Log Entry

```json
{
  "index": 102394,
  "timestamp": "2025-08-31T12:45:00Z",
  "receiptHash": "urn:sha256:8c9f...",
  "saltHash": "urn:sha256:bb31...",
  "policyId": "pack.childcare.readiness.fr@0.1.0",
  "jurisdiction": "FR"
}
```

### Data Model — Signed Tree Head (STH)

```json
{
  "treeSize": 102394,
  "rootHash": "urn:sha256:a82d...",
  "timestamp": "2025-08-31T12:45:05Z",
  "logId": "did:web:cachet.id:transparency-log",
  "signature": "base64-EdDSA-sig..."
}
```

---

### Audit & Verification

- **Inclusion Proofs**: Merkle path from receipt hash to rootHash.
- **Consistency Proofs**: show append‑only property between two STHs.
- **Third‑Party Auditors**: can run monitors; alert community on log equivocation or withholding.
- **User Experience**: Cachet app shows a green check when a receipt hash is included and monitored.

---

### Oversight & Governance

- **Independent Monitors**: NGOs, consumer protection groups, academic labs.
- **Oversight Council**: reviews escalations, can order rollbacks or public incident reports.
- **Transparency Reports**: quarterly publication of log growth, inclusion rates, auditor findings.

---

### Next Steps

1. Implement transparency log MVP using Trillian or Sigsum backend.
2. Integrate Merkle inclusion proof storage into Cachet app.
3. Recruit 2–3 independent auditors in launch regions (FR, EE, ES).
4. Publish Transparency Log Charter under Policy Manifest.
5. Plan quarterly Transparency Report format.

---

---

## Recap

This architecture keeps PII at the edge, uses open standards for issuance/presentation, and centralizes only policy, revocation, and audit primitives. It’s issuer‑agnostic, RP‑friendly, and ready for ZK‑heavy predicates as they mature.
