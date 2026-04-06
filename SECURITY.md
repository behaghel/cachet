# Security Policy

Cachet handles trust infrastructure and privacy-preserving credentials. We take security seriously.

## Reporting a vulnerability

Email **security@cachet.id** with:

- Description of the vulnerability
- Steps to reproduce
- Potential impact assessment

We will acknowledge receipt within **48 hours** and aim to provide an initial assessment within **5 business days**.

## Scope

In scope:

- All backend services (Verifier, Registry, Receipts Log, Issuance Gateway)
- Mobile wallet (Android app, shared KMM module)
- Cryptographic implementations (SD-JWT, credential signing, key management)
- API endpoints and authentication flows
- Transparency log integrity

Out of scope:

- Third-party dependencies (report upstream; let us know if it affects Cachet)
- Social engineering attacks against team members
- Denial-of-service attacks

## Responsible disclosure

- Do not publicly disclose the vulnerability before we have addressed it
- Do not access or modify other users' data beyond what is necessary to demonstrate the vulnerability
- We will not pursue legal action against researchers acting in good faith

## Current security posture

> See `docs/VERIFICATION_PROTOCOL.md` for the full threat model and cryptographic spec, and `docs/SECURITY_HARDENING_PLAN.md` for implementation status and phased delivery plan.
