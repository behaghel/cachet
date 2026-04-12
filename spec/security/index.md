---
domain: security
type: core
status: draft
last-reviewed: 2026-04-12
---

# Security

Cryptographic foundation — JWS/JWE/SD-JWT verification, DID resolution,
key management (StrongBox/Secure Enclave), holder binding, threat model.

## Ubiquitous Language

| Term | Meaning |
|------|---------|
| JWS | JSON Web Signature — cryptographic proof of credential integrity |
| JWE | JSON Web Encryption — encrypted payload for credential transport |
| SD-JWT | Selective Disclosure JWT — holder controls which claims are revealed |
| KB-JWT | Key Binding JWT — proves holder possesses the private key |
| DID | Decentralized Identifier — self-sovereign identity anchor |
| StrongBox | Android hardware security module for key storage |
| Secure Enclave | iOS hardware security module for key storage |
| StatusList2021 | Bitstring-based credential revocation mechanism |

## Key Concepts

<!-- TODO: define trust chain, verification algorithm, key lifecycle -->

## Invariants

<!-- TODO: from VERIFICATION_PROTOCOL.md — 14 threats, crypto requirements -->

## Context Map Relationships

- **wallet** (partnership): Provides crypto primitives for key management
- **verification** (partnership): Provides JWS/JWE verification, DID resolution
- **issuance** (partnership): Provides SD-JWT signing, credential building

## Domain Events

<!-- TODO: key.generated, key.rotated, credential.verified, credential.tampered -->
