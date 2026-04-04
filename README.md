# Cachet

> Prove who you are without giving yourself away.

Today, proving you're trustworthy online means handing over personal data to every party that asks. A parent community checking a caregiver gets their birthdate, ID number, criminal record details, referees' names. The verifier becomes a data controller. The person being verified loses control. Cachet inverts this: cryptographic proofs of **predicates** -- "I am over 18", "my background check is clean", "I have 2+ verified references" -- bundled into reusable **Trust Packs**. The verifier gets exactly the assurance they need. The holder reveals nothing more. Think SSL certificates, but for people.

**Example**: A parent community in Madrid onboards a caregiver in under 2 minutes. Age verified, identity confirmed, background checked, 2+ references -- without ever seeing a birthdate, ID number, or referee name. A consent receipt is logged for auditing. Confidence without becoming a data controller.

## How it works

- **Trust Pack** -- a reusable set of predicates for a context (e.g., "Childcare Readiness", "Safe Seller")
- **Predicate** -- a property proven, not the raw attribute (`age >= 18`, not a birthdate)
- **Badge** -- the verification outcome, time-boxed and contextual ("Childcare-Ready (ES) -- valid 90 days")
- **Consent Receipt** -- signed record of what was proven, to whom, and why; hash anchored to a transparency log
- **Transparency Log** -- append-only Merkle tree so anyone can audit that receipts exist and haven't been tampered with

```
Holder                    Verifier                 Transparency Log
  |                          |                          |
  |-- presents proofs ------>|                          |
  |                          |-- checks policy,         |
  |                          |   signatures,            |
  |                          |   revocation             |
  |<-- Badge + "why" --------|                          |
  |                          |                          |
  |-- Consent Receipt hash --|------------------------->|
  |                          |                          |
```

## Architecture

Four backend services, one mobile wallet. All PII stays on the holder's device; servers handle only policy, proofs, and audit primitives.

| Service | Purpose | Port |
|---------|---------|------|
| Verifier | Validates Trust Pack presentations against signed policies | 8081 |
| Registry | Signed, versioned pack and policy definitions | 8082 |
| Receipts Log | Consent receipts + transparency log | 8083 |
| Issuance Gateway | Converts Veriff identity checks into SD-JWT Verifiable Credentials | 8090 |

The **Cachet Wallet** (Android, KMM) holds credentials, plans proofs locally, and can both present credentials and request verification from peers.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full system design with implementation status.

## Quick start

```bash
devenv shell                          # enter dev environment
devenv shell -- dev:services          # start all backend services
devenv shell -- test:all              # run tests
devenv shell -- android:run           # full setup: backend + Android app
```

After pulling `.envrc` changes, run `direnv allow` once. If shell startup seems stuck, run `./scripts/diagnose-devenv-shell.sh`.

## Trust Packs

| Pack | Context | Predicates |
|------|---------|------------|
| Childcare Readiness | Caregiver onboarding | age >= 18, ID + liveness verified, clean background check, >= 2 verified references |
| Safe Seller | Marketplace trust | ID verified, platform tenure >= 6 months, fulfilment rate >= 95%, no unresolved chargebacks |

Pack definitions with jurisdictional variants (FR, EE, ES) are in [docs/PACKS/](docs/PACKS/).

## Project status

| Layer | Status |
|-------|--------|
| Issuance Gateway | Working -- Veriff webhook to SD-JWT VC via OpenID4VCI |
| Mobile Wallet | Working -- credential vault, issuance flow, consent receipts, Compose UI |
| Verifier | Stub -- returns hardcoded responses; deterministic policy engine planned |
| Registry | Working -- serves signed policy manifest |
| Receipts Log | Stub -- real logic in mobile; backend transparency log planned |
| BBS+ / ZK-SNARKs | Planned -- Phase B |
| iOS Wallet | Planned |

See [docs/ROADMAP.md](docs/ROADMAP.md) for build phases and exit criteria.

## Documentation

| Document | Description |
|----------|-------------|
| [Vision](docs/VISION.md) | Customer story, core concepts, key technologies, product flows |
| [Architecture](docs/ARCHITECTURE.md) | System design with implementation status markers |
| [Roadmap](docs/ROADMAP.md) | Build phases, metrics, exit criteria |
| [Transparency Log](docs/TRANSPARENCY_LOG.md) | Merkle log design for consent receipt auditing |
| [Business Model](docs/BUSINESS_MODEL.md) | Pricing, adoption levers, moat |
| [Refactoring Plan](docs/REFACTORING_PLAN.md) | Engineering cleanup phases with status tracking |
| [Health Endpoints](docs/HEALTH_ENDPOINTS.md) | Why `/health` not `/healthz` (Cloud Run constraint) |
| [CI Optimization](docs/CI_OPTIMIZATION.md) | Caching strategy and build performance |
| [Contributing](CONTRIBUTING.md) | Developer onboarding, schema-first workflow, quality gates |

## Standards

Built on open standards for interoperability:

- **W3C Verifiable Credentials 2.0** -- portable credential format
- **SD-JWT VC** -- selective disclosure (prove predicates without revealing attributes)
- **OpenID4VCI / OpenID4VP** -- standard issuance and presentation protocols
- **DIDs** -- decentralized identifiers with key rotation
- **StatusList2021** -- privacy-preserving revocation

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, schema-first workflow, and quality gates.

## License

Apache-2.0
