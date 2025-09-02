# System Architecture (v0.1)

> Goal: privacy‑preserving, standards‑based trust provider with holder‑controlled data, Veriff as foundational issuer, and clean paths for issuers/RPs to integrate.

## Layered view

### Client layer

- **Cachet Wallet (iOS/Android)**: holder keys in Secure
  Enclave/StrongBox; passkeys for sign‑in; offline QR presentment;
  consent receipts UI; Trust Contacts.
- **RP SDKs**: Web (TS) & Mobile (Kotlin/Swift) for _Request Pack_
  (OID4VP), badge rendering, explainability pane.
- **Issuer Console**: onboard issuer DIDs, schemas, status lists.

### Edge crypto & policy

- **Local Proof Planner** (WASM): composes proofs per Pack using
  SD‑JWT/BBS+/ZK; chooses cheapest option; does not exfiltrate PII.
- **Policy Cache**: signed Policy Manifest & Pack definitions (semver)
  stored locally.

### Core services (cloud)

- **Issuance Gateway** (OID4VCI): Veriff → foundational ID+liveness
  VC; pluggable issuers (justice ministries, platforms, payments).
- **Presentation Verifier** (OID4VP): schema registry, proof
  verification, revocation & freshness checks; returns deterministic
  **Badge**.
- **Pack/Policy Registry**: signed, versioned Pack JSON; jurisdiction
  variants; public fetch.
- **Issuer Registry**: DID documents, schemas, revocation endpoints;
  trust/approval status.
- **Revocation & Status Lists**: StatusList2021 endpoints; short
  soft‑disable windows for appeals.
- **Consent Receipts**: sign receipts client‑side; store hash &
  inclusion proof; RP gets a minimal copy (TTL ≤ 90d).
- **Transparency Log**: append‑only Merkle log + STH API (see v0.4
  design).
- **Vouching Service**: reference capture, verification workflow;
  emits count proofs via ZK circuits.
- **Connector Hub**: marketplace/payment/device connectors; normalizes
  platform stats → credential issuers.
- **Telemetry (privacy‑preserving)**: aggregated metrics, no PII;
  opt‑in debug traces.
- **Ops & Governance**: key ceremony/HSM, oversight workflows, policy
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
