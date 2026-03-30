---
name: Slice 1 decisions
description: Go service architecture refactoring decisions — shared scaffolding, generated types, devenv 2.x ports, delete skeletons
type: project
---

Aligned decisions from Slice 1 (Go service architecture):

1. Use devenv 2.x `ports.allocate` for automatic port allocation; derive env vars from port values
2. Services must import from `generated/go/models` instead of redeclaring types
3. Extract shared scaffolding into `services/common/` (health, server builder, logging)
4. Introduce internal packages in issuance-gateway (oauth, veriff, credential)
5. Delete or gate skeleton services (connector-hub, transparency-log, vouching-service)
6. Standardize all services on Pattern A (main.go + server.go + server_test.go)
7. Fix spec/code alignment: /healthz -> /health in spec, add quality fields, reconcile credentialSubject shape, fix casing to camelCase

**Why:** Current state is copy-paste heavy, ignores generated code, has port collisions, and spec diverges from implementation.
**How to apply:** These decisions inform all subsequent slices and the eventual refactoring work.
