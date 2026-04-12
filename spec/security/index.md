---
domain: security
status: draft
last-reviewed: 2026-04-12
---

# Security

> See spec/domains.yaml for description, classification, code paths, and context map.

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

<!-- TODO: from verification-protocol.md — 14 threats, crypto requirements -->

## Domain Events

<!-- TODO: key.generated, key.rotated, credential.verified, credential.tampered -->
