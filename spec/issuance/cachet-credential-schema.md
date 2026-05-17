---
domain: issuance
status: draft
last-reviewed: 2026-05-09
---

# Cachet Credential Schema v2

## Problem

The current credential schema is designed for single-source, point-in-time
identity verification (Veriff). Behavioral cachets need a schema that supports:

- Multiple evidence sources (Veriff + TrustTrail platforms)
- Composite strength scores with temporal decay
- Metallic tier abstraction (bronze/silver/gold)
- Cachet composability (behavioral cachets require an identity cachet)
- Auto-reissuance as new evidence arrives
- Self-contained credentials (evidence + decay config portable with the credential)

## Design Principles

1. **The tier is the signal.** Verifiers see "Gold Trusted Host" — not individual
   transactions. The Cachet brand backs the tier meaning.
2. **Continuous decay, not fixed expiry.** Evidence loses strength over 12 months.
   The wallet recomputes locally; the holder always wants the freshest snapshot.
3. **Cachets are composable.** An identity cachet is itself a cachet, not a special
   foundation section. Other cachets link to it via `requires`.
4. **Self-contained credentials.** Evidence items and decay config travel with the
   credential — portable, backup-friendly, no external state needed.
5. **Auto-reissuance.** When new evidence is detected, the wallet auto-submits
   to get a fresh credential with updated strength. The holder always presents
   their strongest/freshest posture.

## Schema

### SD-JWT VC Structure

```
Cachet Credential (SD-JWT VC)

NON-DISCLOSABLE (always visible to verifier)
  iss         "did:cachet:production"
  sub         holder DID
  iat         issuance timestamp (Unix)
  jti         credential ID (URN UUID)
  vct         credential type URI
  cnf         holder's JWK (KB-JWT binding)
  tier        "bronze" | "silver" | "gold"
  strength    0.0–1.0 (composite score snapshot at issuance)
  requires    list of prerequisite cachet credential IDs

SELECTIVELY DISCLOSABLE (holder chooses what to reveal)
  strength_detail
    evidence_count      int — total evidence items contributing
    platforms           list of platform names
    date_range          { from: ISO date, to: ISO date }
    trust_levels        { cryptographic: int, mta_attested: int }

  identity_summary      (Identity cachets only)
    verified            boolean
    level               "basic" | "standard" | "premium" | "gold"
    method              "veriff"

HOLDER-ONLY (in credential, never disclosed)
  evidence_items        list of EvidenceItem
  decay_config          DecayConfig
  tier_thresholds       TierThresholds
```

### Types

#### EvidenceItem

Each piece of evidence that contributes to the cachet's strength.

```
EvidenceItem
  type            string — claim type (e.g., "exchange_confirmation", "sale_notification")
  platform        string — source platform (e.g., "homeexchange.com", "vinted")
  date            ISO date — when the evidence event occurred
  trust_level     "cryptographic" | "mta_attested"
  score           0.0–1.0 — extraction confidence at capture time
  dkim_domain     string — DKIM signing domain (provenance proof)
```

#### DecayConfig

Controls how evidence strength decreases over time.

```
DecayConfig
  window_months   int — after this many months, evidence strength reaches zero (default: 12)
  decay_function  "linear" — strength = score × max(0, 1 - age_months/window_months)
```

Linear decay chosen for simplicity and transparency. An exchange from 6 months ago
retains 50% of its original score. At 12 months, it reaches zero.

#### TierThresholds

Defines what composite strength earns each tier.

```
TierThresholds
  bronze          0.3
  silver          0.6
  gold            0.85
```

These thresholds are per-cachet-type. A "Trusted Host" gold may require different
strength than a "Safe Seller" gold. The thresholds travel with the credential so
the wallet can compute tier transitions locally.

### Strength Computation

```
current_strength = sum(
  for each evidence_item:
    item.score × max(0, 1 - age_months(item.date) / decay_config.window_months)
)

// Normalize to 0.0–1.0
current_strength = min(1.0, current_strength / normalization_factor)
```

The `normalization_factor` is cachet-type-specific and capped — it represents
what "perfect evidence" looks like. Once reached, more evidence doesn't increase
strength beyond 1.0. This keeps the tier system meaningful (gold = excellent,
not "has the most data").

Example normalization factors:
- Trusted Host: 10 confirmed exchanges over 12 months at cryptographic trust
- Safe Seller: 15 completed sales over 12 months
- Identity: 1 Veriff gold-tier verification

### Credential Types (vct)

| vct URI | Description | Requires |
|---------|-------------|----------|
| `cachet:Identity` | Foundational identity from Veriff | — |
| `cachet:TrustedHost` | Home exchange / hosting trust | `cachet:Identity` |
| `cachet:SafeSeller` | Peer-to-peer selling trust | `cachet:Identity` |
| `cachet:ChildcareReady` | Childcare provider readiness | `cachet:Identity` |

### Composability — the `requires` field

A cachet can require other cachets as prerequisites:

```json
{
  "vct": "cachet:TrustedHost",
  "tier": "gold",
  "strength": 0.91,
  "requires": ["urn:uuid:abc-123-identity-cachet-jti"]
}
```

The `requires` array contains `jti` values of prerequisite cachets. The verifier
can optionally request the prerequisite credentials in the same presentation
to verify the chain, or trust the issuer's attestation that prerequisites were
checked at issuance time.

**Prerequisite validation at issuance:**
- The issuance gateway checks that the holder has valid prerequisite cachets
  before issuing a dependent cachet
- "Valid" means: the prerequisite exists, is not revoked, and its strength
  is above the minimum tier (bronze)
- If a prerequisite decays below bronze, the dependent cachet's `requires`
  check fails on next re-issuance — the holder is prompted to refresh their
  identity or behavioral evidence

### Auto-Reissuance Flow

```
TrustTrail detects new evidence (polling cycle)
    ↓
Evidence extracted on-device, claims added to local store
    ↓
Wallet computes updated strength with new evidence
    ↓
Wallet auto-submits evidence bundle to issuance gateway
    ↓
Issuance gateway validates:
  - Holder has valid identity cachet (requires check)
  - Evidence bundle is well-formed
  - Trust levels verified (DKIM proofs accompany claims)
    ↓
New SD-JWT VC issued with fresh iat, updated strength/tier
    ↓
Old credential superseded (not revoked — just stale)
    ↓
Wallet stores new credential, discards old
```

### Tier Degradation (Local)

The wallet continuously recomputes strength as evidence ages:

```
On app launch / periodic timer:
  current_strength = compute_strength(evidence_items, decay_config)
  current_tier = tier_for(current_strength, tier_thresholds)

  if current_tier < credential.tier:
    Show: "Your Trusted Host is approaching Silver.
           Scan for new evidence to maintain Gold."

  if current_tier < bronze:
    Show: "Your Trusted Host has expired.
           Connect your inbox to rebuild it."
```

No network call needed — decay is computable from the credential's own data.

### Migration from Schema v1

Current v1 credentials (Veriff-only, StatusList2021) remain valid. The v2 schema
is additive:

- v1 credentials become `cachet:Identity` type
- StatusList2021 is deprecated — revocation moves to credential supersession
  (new credential issued → old is stale, not explicitly revoked)
- v1 `evidence` array maps to `evidence_items` in the holder-only section
- v1 `verificationLevel` maps to `tier`
- v1 `verificationMetrics` map to `strength_detail`

### Example: Gold Trusted Host

```json
{
  "iss": "did:cachet:production",
  "sub": "did:example:holder-123",
  "iat": 1746835200,
  "jti": "urn:uuid:e7b2f3a1-...",
  "vct": "cachet:TrustedHost",
  "cnf": { "jwk": { ... } },

  "tier": "gold",
  "strength": 0.91,
  "requires": ["urn:uuid:abc-123-identity-cachet-jti"],

  "_sd": ["hash-of-strength_detail", "hash-of-evidence_items", "..."],
  "_sd_alg": "sha-256"
}

// Selectively disclosable: strength_detail
// ["salt", "strength_detail", {
//   "evidence_count": 7,
//   "platforms": ["homeexchange.com"],
//   "date_range": { "from": "2025-06", "to": "2026-05" },
//   "trust_levels": { "cryptographic": 5, "mta_attested": 2 }
// }]

// Holder-only (in credential but never presented)
// ["salt", "evidence_items", [
//   { "type": "exchange_confirmation", "platform": "homeexchange.com",
//     "date": "2026-03-15", "trust_level": "cryptographic",
//     "score": 0.95, "dkim_domain": "homeexchange.com" },
//   ...
// ]]
// ["salt", "decay_config", { "window_months": 12, "decay_function": "linear" }]
// ["salt", "tier_thresholds", { "bronze": 0.3, "silver": 0.6, "gold": 0.85 }]
```
