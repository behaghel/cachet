---
domain: admin
status: draft
last-reviewed: 2026-04-19
---

# Admin API Specification

## Service

- **Name**: admin
- **Port**: 8091 (`CACHET_ADMIN_PORT`)
- **Type**: Internal-only (not public-facing)
- **Dependencies**: registry (8082), issuance-gateway (8090), relay (8084), verifier (8081)

## Authentication

### API Key (v1.0)

All requests must include the `X-API-Key` header matching the `ADMIN_API_KEY` environment variable.

| Scenario | Response |
|----------|----------|
| Missing header | 401 `{"error": "missing_api_key"}` |
| Invalid key | 401 `{"error": "invalid_api_key"}` |
| No ADMIN_API_KEY configured | Service refuses to start |

Future: GCP IAP or mTLS for production operators.

---

## Endpoints

### Status

#### `GET /admin/status`

Returns service info. No side effects.

**Response 200:**
```json
{
  "service": "admin",
  "version": "0.1.0",
  "uptime_seconds": 3600
}
```

---

### Pack Management

All pack mutations are audit-logged.

#### `GET /admin/packs`

Lists all packs (delegates to registry `GET /registry/packs`).

**Response 200:** Array of `PackDefinition`.

#### `GET /admin/packs/{id}`

Gets a single pack (delegates to registry `GET /registry/packs/{id}`).

**Response 200:** `PackDefinition`
**Response 404:** `{"error": "not_found"}`

#### `POST /admin/packs`

Creates a new pack definition. Writes to overlay directory, triggers registry reload.

**Request body:** `PackDefinition` JSON.

**Validation:**
- `id` must not conflict with existing pack
- `id`, `name`, `version` are required
- `predicates` array must be non-empty

**Response 201:** Created `PackDefinition`
**Response 400:** Validation error
**Response 409:** Pack ID already exists

**Audit:** `pack.created` with resource=pack.id

#### `PUT /admin/packs/{id}`

Updates an existing pack. Writes to overlay (shadows embedded version).

**Request body:** `PackDefinition` JSON. `id` in body must match URL param.

**Response 200:** Updated `PackDefinition`
**Response 400:** Validation error
**Response 404:** Pack not found

**Audit:** `pack.updated` with resource=pack.id

#### `PATCH /admin/packs/{id}/status`

Enables or disables a pack without deleting it.

**Request body:**
```json
{"enabled": false}
```

**Response 200:** `{"id": "...", "enabled": false}`
**Response 404:** Pack not found

**Audit:** `pack.disabled` or `pack.enabled` with resource=pack.id

---

### Credential Revocation

#### `POST /admin/credentials/{index}/revoke`

Revokes a credential by status list index. Delegates to issuance gateway `POST /status/{listId}/revoke`.

**Request body:**
```json
{
  "listId": "1",
  "reason": "operator-initiated"
}
```

`listId` defaults to "1" if omitted.

**Response 200:** `{"revoked": true, "index": 42, "listId": "1"}`
**Response 400:** Invalid index
**Response 404:** Status list not found

**Audit:** `credential.revoked` with resource=`list:{listId}/index:{index}`

#### `GET /admin/statuslist/{id}`

Returns status list statistics.

**Response 200:**
```json
{
  "id": "1",
  "purpose": "revocation",
  "allocated": 150,
  "revoked": 3,
  "capacity": 131072
}
```

---

### Session Management

#### `GET /admin/sessions`

Aggregates active session counts from relay and verifier.

**Response 200:**
```json
{
  "relay": {"active": 5, "oldest_age_seconds": 120},
  "verifier": {"active": 3, "oldest_age_seconds": 45}
}
```

#### `DELETE /admin/sessions/{service}/{id}`

Force-expires a session. `service` is `relay` or `verifier`.

**Response 204:** Session expired (or already gone)
**Response 400:** Invalid service name

**Audit:** `session.force_expired` with resource=`{service}:{id}`

---

## Error Response Format

All errors follow the existing `common.WriteError` format:

```json
{
  "error": "error_code",
  "message": "Human-readable description"
}
```

## Audit Log Schema

Every audit entry is a zerolog JSON line with these fields:

| Field | Type | Description |
|-------|------|-------------|
| `audit` | bool | Always `true` — used by GCP log sink filter |
| `action` | string | Domain event name (e.g., `pack.created`) |
| `resource` | string | Identifier of the affected resource |
| `outcome` | string | `success` or `failure` |
| `actor` | string | API key identifier (when available) |
| `timestamp` | string | RFC3339 UTC timestamp |
| `request_id` | string | Correlation ID from request middleware |

GCP log sink filter: `jsonPayload.audit = true`

## Internal Service Endpoints

These endpoints are added to existing services for admin consumption. They are NOT exposed publicly.

### Registry

- `POST /internal/reload` — triggers pack store hot-reload from overlay directory

### Issuance Gateway

- `GET /status/{listId}/info` — returns allocated/revoked/capacity counts

### Relay

- `GET /internal/sessions` — lists active sessions with age
- `DELETE /internal/sessions/{id}` — force-expires a session

### Verifier

- `GET /internal/sessions` — lists active verification sessions with age
- `DELETE /internal/sessions/{id}` — force-expires a session
