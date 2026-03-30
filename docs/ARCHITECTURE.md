# System Architecture (v0.1)

> Goal: privacy‑preserving, standards‑based trust provider with holder‑controlled data, Veriff as foundational issuer, and clean paths for issuers/RPs to integrate.

> **Implementation status key:** Items marked **(implemented)** exist in code today. Items marked **(planned)** are architectural targets not yet built. Items with no marker are partially implemented or in progress.

## Layered view

### Client layer

- **Cachet Wallet (Android)** **(implemented)**: KMM shared logic + Jetpack Compose UI;
  credential vault (SQLDelight); OpenID4VCI issuance flow; consent receipts; quality display.
  _iOS target declared but not built. Passkeys, offline QR, Trust Contacts are planned._
- **RP SDKs** **(planned)**: Web (TS) & Mobile (Kotlin/Swift) for _Request Pack_
  (OID4VP), badge rendering, explainability pane. _Stub directories exist._
- **Issuer Console** **(planned)**: onboard issuer DIDs, schemas, status lists.

### Edge crypto & policy

- **Local Proof Planner** **(planned)** (WASM): composes proofs per Pack using
  SD‑JWT/BBS+/ZK; chooses cheapest option; does not exfiltrate PII.
- **Policy Cache** **(planned)**: signed Policy Manifest & Pack definitions (semver)
  stored locally.

### Core services (cloud)

- **Issuance Gateway** (OID4VCI) **(implemented)**: Veriff → foundational ID+liveness
  VC; pluggable issuers (justice ministries, platforms, payments).
- **Presentation Verifier** (OID4VP) **(stub)**: schema registry, proof
  verification, revocation & freshness checks; returns deterministic
  **Badge**. _Currently returns hardcoded stub responses._
- **Pack/Policy Registry** **(implemented)**: signed, versioned Pack JSON; jurisdiction
  variants; public fetch. _Serves static manifest._
- **Issuer Registry** **(planned)**: DID documents, schemas, revocation endpoints;
  trust/approval status.
- **Revocation & Status Lists** **(planned)**: StatusList2021 endpoints; short
  soft‑disable windows for appeals. _CredentialStatus type exists but no revocation service._
- **Consent Receipts** **(implemented)**: sign receipts client‑side; store hash &
  inclusion proof; RP gets a minimal copy (TTL ≤ 90d). _Backend is a stub; mobile has real logic._
- **Transparency Log** **(stub)**: append‑only Merkle log + STH API (see v0.4
  design). _Receipts-log returns hardcoded responses. Mobile has mock implementation._
- **Vouching Service** **(planned)**: reference capture, verification workflow;
  emits count proofs via ZK circuits.
- **Connector Hub** **(planned)**: marketplace/payment/device connectors; normalizes
  platform stats → credential issuers.
- **Telemetry (privacy‑preserving)** **(planned)**: aggregated metrics, no PII;
  opt‑in debug traces.
- **Ops & Governance** **(planned)**: key ceremony/HSM, oversight workflows, policy
  changelog signer.

### Secure compute

- **TEE workers** (SGX/SEV‑SNP) for any server‑side policy transforms
  that must not see plaintext; remote attestation receipts.

## Cryptography & standards

- **Credentials**: W3C VC 2.0; **SD‑JWT VC** for selective disclosure;
  **BBS+** for unlinkability; ZK‑SNARKs (Plonk/Halo2) for aggregate
  predicates.
- **Protocols**: **OpenID4VCI** (issuance), **OpenID4VP**
  (presentation), **DID** for identifiers; **StatusList2021**
  revocation.
- **Media authenticity**: verify **C2PA** on submitted photos/videos;
  show provenance flags.

## Data topology

- **Holder device vault**: all PII & credentials; encrypted backup
  (optional, split‑key). Cachet servers never store raw claims.
- **Server stores**: policy/pack registry, issuer registry, revocation
  lists, transparency log, aggregated telemetry.
- **Jurisdictional sharding**: regional deployments (EU‑West primary)
  with data residency for any issuer integration that mandates it.

## Core APIs (external)

- **OID4VCI**: `/oauth/token`, `/credential` (per schema).
- **OID4VP**: `/authorize`, `/par`, `/presentation` (verifier);
  `nonce` & `state` anti‑replay.
- **Packs**: `GET /packs`, `GET /packs/{id}@{version}`.
- **Verify**: `POST /presentations/verify` → `{badge, predicates, freshness}`.
- **Receipts**: `POST /receipts/hash`, `GET /receipts/{id}`
  (holder‑scoped), `GET /log/sth`, `GET /log/proof?hash=...`.
- **Issuers**: `POST /issuers/register`, `GET /issuers`, `GET
/.well-known/did.json`.

## Key flows (sequence summaries)

### Issuance (foundational VC)

1. Holder completes Veriff flow → Issuance Gateway obtains attested result.
2. Gateway issues SD‑JWT VC (ID+liveness), writes revocation entry, returns to wallet via OID4VCI.

### Request Pack / Present Proof

1. RP SDK calls `/authorize` with `policyId` & purpose →
   QR/deeplink.
2. Wallet pulls policy, plans proofs locally, assembles
   SD‑JWT/BBS+/ZK bundle.
3. RP sends bundle to Verifier → checks schemas, signatures,
   revocation, freshness, jurisdiction → returns **Badge** +
   explainability.
4. Wallet emits **Consent Receipt**, anchors hash to Transparency
   Log; RP stores minimal copy (TTL ≤ 90d).

### Vouching (references ≥ 2)

1. Candidate invites referees → attest inside Cachet (lightweight
   identity + relationship proof).
2. Vouching Service verifies, issues a private set‑membership
   credential.
3. Wallet proves `count ≥ 2` via ZK without revealing identities.

### Revocation/appeal

1. Issuer updates StatusList; Verifier respects soft‑disable window.
2. Holder files appeal; Oversight workflow can re‑enable pending review.

## Security model

> **Note:** The items below are architectural targets. Current implementation uses in-memory RSA key generation (not HSM), has no webhook signature verification, no rate limiting, and no replay protection. See `docs/REFACTORING_PLAN.md` Phase 3 for the security hardening plan.

- **Keys**: device hardware‑backed; passkeys for account; recovery via split‑key (user device + recovery contact).
- **Signers**: HSM‑backed for Registry, Log STH, and Issuance Gateway.
- **Replay & phishing**: OID4VP nonces, audience binding, short‑lived presentations; QR with origin pinning.
- **Supply chain**: SBOM, SLSA‑L3 builds, image signing, provenance checks.
- **Abuse**: RP rate‑limits, purpose binding, anomaly detection on request patterns.

## Observability & SRE

- **Metrics**: time‑to‑trust, verification pass rate, revocation
  lookups, log inclusion latency.
- **Tracing**: redaction‑safe spans; correlation via request IDs only.
- **Reliability**: multi‑AZ, blue/green deploys, WAF & DDoS
  protection, circuit breakers on issuer/connectors.

## Tech stack (suggested)

- **Mobile**: Kotlin Multiplatform Mobile (KMM) + native UI layers;
  alt: React Native + native crypto bridges.
- **Crypto**: WebAssembly proof planner; sd‑jwt libs, bbs‑signature
  libs; gnark/halo2 for ZK circuits.
- **Backend**: Go or Rust microservices; gRPC internally; Postgres
  (registry), Redis (nonces), object store (artifacts),
  Trillian/Sigsum (transparency).
- **Infra**: Kubernetes, Istio mTLS, HashiCorp Vault, HSM (CloudHSM),
  TEEs for sensitive transforms.
- **SDKs**: TypeScript, Swift, Kotlin; OpenAPI for REST; OIDC
  certified where applicable.

## Boundaries for AI/agents

- **Agent UX**: LLM‑driven assistant explains outcomes, drafts
  requests, and helps holders/RPs navigate. It **never** makes the
  final decision; the **Verifier** applies deterministic policy.
- **Safety**: retrieval‑augmented from signed Policy Manifest;
  tool‑use limited to read‑only registry and renderer; prompts logged
  locally.
