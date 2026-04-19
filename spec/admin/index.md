---
domain: admin
status: draft
last-reviewed: 2026-04-19
---

# Admin (Backoffice)

> See spec/domains.yaml for description, classification, code paths, and context map.

## Ubiquitous Language

| Term | Meaning |
|------|---------|
| Operator | A human or system with admin API credentials who manages system configuration |
| Pack Overlay | Writable file-system directory that shadows embedded pack definitions at runtime |
| Hot Reload | Signalling the registry to re-read pack definitions without restart |
| Audit Entry | A structured log record (audit=true) for security-sensitive operations |
| Status List Info | Summary of a StatusList2021 bitstring: allocated count, revoked count, capacity |

## Key Concepts

The admin service is a **supporting** domain that acts as a control plane for operators. It does not implement business logic — it delegates to core services (registry, issuance-gateway, relay, verifier) via their internal APIs.

### Separation of concerns

- **Admin service** owns: authentication, request validation, audit logging, and API aggregation
- **Registry** owns: pack storage, pack validation, pack serving
- **Issuance gateway** owns: revocation execution, status list storage
- **Relay/Verifier** own: session lifecycle

The admin service never bypasses a core service's API. For example, revoking a credential goes through the issuance gateway's existing `/status/{listId}/revoke` endpoint.

## Invariants

1. Every mutating admin operation is audit-logged (action, actor, resource, outcome, timestamp)
2. Unauthenticated requests receive 401; invalid API keys receive 401
3. Pack creation validates JSON against the PackDefinition schema before writing
4. Pack overlay writes are atomic (temp file + rename)
5. Hot reload is non-blocking — the registry builds a new map, then swaps atomically
6. Revocation is irreversible — once a bit is set, it cannot be unset
7. Session force-expire is immediate and idempotent

## Domain Events

- `pack.created` — new pack written to overlay
- `pack.updated` — existing pack version updated
- `pack.disabled` / `pack.enabled` — pack status toggled
- `pack.reloaded` — registry hot-reload triggered
- `credential.revoked` — status list bit set via admin
- `session.force_expired` — stuck session cleaned up
