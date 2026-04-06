# Roadmap

## Metrics that matter

### Reliability and speed

- **Time-to-trust (TTT)**: request -> badge (p50/p95)
- **Verification success rate**: % requests resulting in badge without retry
- **Issuer SLOs**: availability/latency of revocation and issuance endpoints
- **Anchoring latency**: time to include receipt hash in transparency log

### Quality and safety

- **Appeal rate and resolution time**; false-positive/negative analysis
- **Consent comprehension**: % holders who confirm they understood what was proven/shared
- **Privacy budget**: % presentations at predicate-only (no raw attributes)

### Adoption and growth

- **Pack reuse rate**: recipients who reuse their Pack within 30/90 days
- **RP conversion**: legacy -> native OID4VP
- **Virality coefficient**: invites generated per completed assessment

## Build phases

### Phase A -- MVP (0-3 months)

Wallet core (keys/passkeys, vault), two Packs (Childcare, Safe Seller), Verifier with deterministic policy, Issuance via Veriff, Consent Receipts + basic Transparency Log, RP SDKs (web/mobile).

**Exit criteria**: p95 TTT < 2m; >= 85% predicate-only presentations; 2 live RP pilots.

### Phase B -- Proof depth (3-6 months)

Vouching ZK flow, platform connectors (>=2), device attestation predicate, BBS+ for unlinkability, jurisdictional Pack variants (FR/EE/ES).

**Exit criteria**: Pack reuse rate >= 35%; issuer SLOs >= 99.9%/30d; appeals resolved median < 5d.

### Phase C -- Scale and governance (6-9 months)

Trust Contacts, Policy Studio, oversight council, full transparency reporting, TEEs for sensitive transforms.

**Exit criteria**: >= 10 enterprise RPs; transparency log monitored by >= 2 independent auditors.
