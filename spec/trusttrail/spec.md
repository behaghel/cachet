---
domain: trusttrail
status: draft
last-reviewed: 2026-05-03
---

# TrustTrail — Behavioral Spec

## Problem

Users have years of transactional emails proving real-world behavior — sales on
Vinted, stays on HomeExchange, bookings on Care.com — but this data is locked
in their inboxes and invisible to trust systems. TrustTrail extracts verified,
structured claims from these emails entirely on-device, giving users a way to
build a behavioral trust profile under the seal of Cachet.

The critical user proposition: **"nothing leaves your device"** — raw email
content and OAuth tokens stay on the phone. Only user-approved, structured
claims are transmitted to the issuance gateway.

Behavioral cachets complement foundational identity (Veriff). A user must have
a foundational cachet before behavioral evidence can enhance their profile.

## Context

### Existing Foundation

- `tools/dkim-explorer/` — Go CLI, ~1,700 LOC: parses DKIM, verifies signatures
  against DNS, extracts claims from 5 platforms via 20+ regex patterns.
  Well-tested with real `.eml` fixtures.
- The Kotlin shared module has Clean Architecture (UseCase → Repository → SQLDelight),
  Koin DI, hardware-backed keys, offline-first sync.
- No existing email/OAuth code in the mobile wallet.

### What Gets Ported

The claim extraction logic from `tools/dkim-explorer/internal/claims/` is ported
to Kotlin in the shared module. The Go tool remains as the exploration/prototyping
workbench. The two codebases evolve independently.

### References

- `tools/dkim-explorer/internal/claims/patterns.go` — porting source of truth for platform patterns
- `tools/dkim-explorer/internal/claims/extract.go` — extraction pipeline to port
- `tools/dkim-explorer/internal/claims/extract_test.go` — test cases to port (20+ scenarios)
- `tools/dkim-explorer/internal/dkim/verify.go` — DKIM verification logic reference
- `tools/dkim-explorer/testdata/fixtures/` — real `.eml` files for integration tests
- `spec/issuance/spec.md` — downstream consumer (evidence bundle → cachet issuance)
- `spec/security/verification-protocol.md` — cryptographic constraints and threat model

---

## Decisions

| # | Decision | Ruling | Rationale |
|---|----------|--------|-----------|
| D1 | DKIM verification strategy | Gmail = `cryptographic` (re-verify on device), Outlook = `mta_attested` (trust Authentication-Results header) | Outlook breaks DKIM body hashes during storage; Gmail preserves them. Per-MTA config. |
| D2 | Go → Kotlin strategy | Port patterns.go to Kotlin in shared module | Patterns are mostly data (regex + metadata), not complex logic. Enables KMP/iOS. Go stays as exploration tool. |
| D3 | OAuth library choice | Google Sign-In SDK (Gmail) + MSAL (Outlook) | Native consent screens build user trust. Familiar UX matters for a wallet app. |
| D4 | Two-phase pull | Headers first (From, Subject, Date), full MIME only if platform matches | Minimizes data fetched. Respects privacy. Reduces quota usage. |
| D5 | What leaves the device | Extracted claims + DKIM verification proof only. Raw content + OAuth tokens: never. | Minimum for issuance. User has consented to those specific claims. |
| D6 | Push vs. poll | Periodic polling (foreground + WorkManager, >= 15 min). No server-side push. | Keeps architecture fully device-side. No server endpoint needed. |
| D7 | Scan depth | Default 6 months, configurable | Balances cold start value vs. API quota. Can be extended later. |
| D8 | Account multiplicity | Single Gmail account initially; code structured for multiple | Avoid complexity now, don't paint ourselves into a corner. |
| D9 | Token refresh failure | Prompt user to reconnect | Explicit is better than silent failure for a trust app. |
| D10 | Confidence threshold | Minimum 0.7 — claims below this are filtered out | Generic fallback patterns (0.4–0.6) are too unreliable. Known-platforms-only means this rarely fires. |
| D11 | Deduplication | Deferred | Tomorrow's problem. |
| D12 | Claim staleness | Recency wins — recent claims weighted higher | Weighting formula TBD in issuance spec. |
| D13 | Module isolation | Separate package, behind feature flag, zero imports from existing wallet domain | Clean boundary. Feature flag gates all entry points. |
| D14 | DKIM verification failure (Gmail) | Fallback to MTA attestation if Authentication-Results contains `dkim=pass`; otherwise reject with "broken_signature" | Cryptographic failure doesn't mean the email is fake — the key may have rotated. MTA saw it pass at delivery time. If MTA also lacks proof, reject. |
| D15 | Scan cursor persistence | Message timestamp stored in SQLite; resume scans from last-processed timestamp | Simple, provider-agnostic (works for Gmail and Outlook), survives app restarts. |

---

## Acceptance Criteria

### 1. OAuth Connection

```gherkin
Scenario: Provider picker
  Given the user has no email provider connected
  When they tap "Connect Email" on the TrustTrail screen
  Then they see a provider picker (Gmail, Outlook)

Scenario: Gmail OAuth scope
  Given the user selects Gmail
  When the Google Sign-In consent screen appears
  Then the requested scope is gmail.readonly
  And no other scope is requested

Scenario: Outlook OAuth scope
  Given the user selects Outlook
  When the Microsoft consent screen appears
  Then the requested scope is Mail.Read
  And no other scope is requested

Scenario: Token storage
  Given the user completes OAuth consent
  When the token is received
  Then the token is stored encrypted in the Android Keystore
  And no token is sent to the backend

Scenario: Disconnect provider
  Given the user has a connected provider
  When they tap "Disconnect" for that provider
  Then the OAuth token is deleted from device
  And no further polling occurs for that provider
```

### 2. Platform Discovery (Cold Start)

```gherkin
Scenario: Headers-only scan
  Given the user has connected Gmail
  When the initial scan begins
  Then only message headers (From, Subject, Date) are fetched
  And messages up to the configured scan depth (default 6 months) are included

Scenario: Known platform detected
  Given headers have been fetched
  When the From domain matches a known platform (e.g., vinted.es)
  Then that platform appears in the "Discovered Platforms" list
  And no email body content has been fetched yet

Scenario: Unknown sender ignored
  Given headers have been fetched
  When the From domain does not match any known platform
  Then that email is ignored entirely
  And its body is never fetched

Scenario: Scan progress
  Given the initial scan is in progress
  When the user views the TrustTrail screen
  Then a progress indicator shows emails scanned vs. total

Scenario: Scan resumption across sessions
  Given the initial scan was interrupted (app killed, quota hit, offline)
  When the scan resumes (next app launch or polling cycle)
  Then it continues from the last-processed message timestamp
  And does not re-fetch already-scanned messages

Scenario: Quota exhaustion
  Given the Gmail API returns HTTP 429
  When the scanner encounters the rate limit
  Then it backs off exponentially
  And resumes from where it left off
  And the user sees "Scan paused — will resume shortly"
```

### 3. Platform Consent

```gherkin
Scenario: Platform list presentation
  Given platforms have been discovered
  When the user sees the platform list
  Then each platform shows: name, icon, number of emails found
  And all platforms are unchecked by default

Scenario: Selective consent
  Given the user checks "Vinted" and "HomeExchange"
  When they tap "Process Selected"
  Then only emails from those two platforms are fetched in full
  And unchecked platform emails are never read

Scenario: New platform suggestion
  Given the user has consented to process Vinted
  And a new email arrives from Care.com (known but not consented)
  When the user opens the TrustTrail screen
  Then they see a suggestion: "We found emails from Care.com. Process them?"
  And no Care.com content is read until they consent
```

### 4. Claim Extraction (On-Device)

```gherkin
Scenario: Platform-specific extraction
  Given the user has consented to process Vinted emails
  When full MIME content is fetched for a Vinted email
  Then claims are extracted using Vinted-specific patterns
  And the raw email content is not persisted to any storage
  And only structured claims are persisted locally

Scenario: DKIM cryptographic verification success (Gmail)
  Given the email is from a Gmail-connected inbox
  When DKIM verification runs on-device
  And the DKIM signature is valid against DNS public key
  Then the trust level is set to "cryptographic"

Scenario: DKIM cryptographic verification failure with MTA fallback (Gmail)
  Given the email is from a Gmail-connected inbox
  When DKIM verification fails (signature mismatch, key rotated, DNS error)
  And the Authentication-Results header contains "dkim=pass"
  Then the trust level is set to "mta_attested"
  And claims are extracted normally

Scenario: DKIM cryptographic verification failure without MTA fallback (Gmail)
  Given the email is from a Gmail-connected inbox
  When DKIM verification fails
  And the Authentication-Results header does NOT contain "dkim=pass"
  Then the email is rejected with reason "broken_signature"
  And no claims are extracted

Scenario: MTA attestation (Outlook)
  Given the email is from an Outlook-connected inbox
  When the Authentication-Results header contains "dkim=pass"
  Then the trust level is set to "mta_attested"
  And no DKIM body hash verification is attempted

Scenario: MTA attestation failure (Outlook)
  Given the email is from an Outlook-connected inbox
  When the Authentication-Results header does NOT contain "dkim=pass"
  Then the email is skipped
  And no claims are extracted

Scenario: Forward rejection
  Given the email subject starts with "Fwd:", "Tr:", "Wg:", or other forward prefix
  Or the email body contains forwarding markers
  When extraction is attempted
  Then the email is rejected with reason "forwarded_email"
  And no claims are extracted

Scenario: Confidence filtering
  Given a claim is extracted with confidence below 0.7
  When the extraction results are compiled
  Then that claim is excluded from the results
```

### 5. Evidence Review & Submission

```gherkin
Scenario: Evidence summary
  Given claims have been extracted from multiple emails
  When the user views the evidence summary
  Then claims are grouped by platform
  And each claim shows: type, date, confidence, trust level

Scenario: Claim deselection
  Given the user reviews their claims
  When they deselect specific claims
  Then deselected claims are excluded from the evidence bundle

Scenario: Evidence submission
  Given the user taps "Submit Evidence"
  When the evidence bundle is sent to the issuance gateway
  Then only user-approved claims are included
  And each claim includes: type, fields, confidence, trust_level, platform, date
  And raw email content is NOT included
  And the DKIM verification proof (domain, result) is included

Scenario: Foundational identity required
  Given the user does NOT have a foundational identity cachet
  When they attempt to submit behavioral evidence
  Then submission is blocked
  And they are prompted to complete identity verification first
```

### 6. Continuous Monitoring

```gherkin
Scenario: New email detection
  Given the user has consented to process Vinted emails
  When a new Vinted email arrives in their inbox
  Then it is detected on the next polling cycle
  And claims are extracted automatically
  And the user is notified of new evidence

Scenario: Background polling
  Given the app is in the background
  When a polling cycle runs via WorkManager
  Then only headers are fetched
  And full content is fetched only for consented platforms
  And polling interval is >= 15 minutes

Scenario: Offline resilience
  Given the app has been offline
  When connectivity is restored
  Then pending evidence submissions are retried
  And the polling cycle resumes
```

### 7. Privacy & Security

```gherkin
Scenario: No raw content leaves device
  Given the inbox scanner is operating
  When any email is processed
  Then raw email content never leaves the device
  And OAuth tokens never leave the device
  And only user-approved extracted claims are transmitted

Scenario: No raw content persisted
  Given the device storage is examined
  When looking for email data
  Then no raw email bodies are stored in SQLite
  And only structured claims exist in the local database
  And OAuth tokens are encrypted in the Android Keystore

Scenario: Data destruction on uninstall
  Given the user uninstalls the app
  When device storage is cleared
  Then all OAuth tokens are destroyed
  And all locally stored claims are destroyed
```

### 8. Feature Flag

```gherkin
Scenario: Flag disabled
  Given the TRUSTTRAIL_ENABLED feature flag is OFF
  When the user navigates the app
  Then no TrustTrail UI is visible
  And no OAuth prompts appear
  And no email polling occurs
  And no evidence-related code paths execute

Scenario: Flag enabled
  Given the TRUSTTRAIL_ENABLED feature flag is ON
  When the user navigates to the TrustTrail screen
  Then the full TrustTrail experience is available
```

---

## Scope

### Module Structure

```
mobile/shared/src/commonMain/kotlin/id/cachet/wallet/trusttrail/
├── model/                   # EvidenceClaim, PlatformPattern, TrustLevel, ScanProgress
├── extraction/              # Kotlin port of patterns.go + extract.go
├── dkim/                    # DKIM verifier + MTA attestation parser
├── provider/                # EmailProvider interface + Gmail/Outlook impls
├── repository/              # TrustTrailRepository (SQLDelight)
├── usecase/                 # InboxScannerUseCase, EvidenceSubmissionUseCase
└── sync/                    # PollingScheduler, EvidenceQueue

mobile/androidApp/src/main/kotlin/.../trusttrail/
├── ui/                      # Compose screens (connect, discover, review)
├── viewmodel/               # TrustTrailViewModel
└── di/                      # TrustTrailModule (Koin)
```

### May Modify

| File/Area | Change |
|-----------|--------|
| `mobile/shared/.../trusttrail/` | New package (all new files) |
| `mobile/androidApp/.../trusttrail/` | New package (all new files) |
| `mobile/shared/build.gradle.kts` | Add email parsing / DKIM dependencies |
| `mobile/androidApp/build.gradle.kts` | Add Google Sign-In SDK, MSAL |
| `AndroidManifest.xml` | OAuth callback intent filter, network permissions |
| SQLDelight schema | New migration: tables for claims, provider tokens, scan state |
| `mobile/androidApp/.../navigation/` | Add TrustTrail routes (behind flag) |
| `mobile/androidApp/.../di/` | Register TrustTrailModule (behind flag) |
| `spec/domains.yaml` | Add trusttrail domain entry |

### Must NOT Modify

| File/Area | Reason |
|-----------|--------|
| `services/*` | Backend changes are a separate spec |
| `tools/dkim-explorer/` | Go tool stays as-is |
| Existing SQLDelight tables | No schema changes to credentials, consent_receipts, etc. |
| Existing use cases / repositories | No coupling to IssuanceUseCase, VerificationUseCase |
| `spec/security/` | Security protocol unchanged |
| CI/CD pipelines | No changes to existing workflows |

---

## Invariants

1. All existing tests pass — `android:test-unit`, `android:bdd`, `test:all`
2. Feature flag OFF = zero TrustTrail code paths execute
3. No raw email content in SQLite — only structured `EvidenceClaim` records
4. The only network call to cachet backend is evidence bundle submission
   to the issuance gateway — no other backend calls from TrustTrail
5. OAuth tokens stored in Android Keystore — never in SharedPreferences or SQLite
6. `trusttrail/` package has zero imports from `wallet/domain/`, `wallet/network/`,
   or any other existing wallet package

### Dependency Direction

```
trusttrail/ ──→ (nothing in existing wallet code)
existing wallet code ──→ (nothing in trusttrail/)
navigation + DI glue ──→ trusttrail/ (gated by feature flag)
```

---

## Verification Plan

| Criterion | Method | Auto? |
|-----------|--------|-------|
| OAuth scope is minimal (gmail.readonly / Mail.Read) | Unit test: assert scope string in config | Yes |
| Token stored in Keystore | Instrumented test: verify KeyStore entry | Yes |
| Token never sent to backend | Unit test: mock HTTP, assert no outbound with token | Yes |
| Headers-only fetch (phase 1) | Unit test: mock Gmail, assert `format=METADATA` | Yes |
| Platform detection from From domain | Unit test: port platform detection cases | Yes |
| Full MIME only for consented platforms | Unit test: mock client, assert selective fetch | Yes |
| Claim extraction correctness | Unit tests: port all 20+ patterns from Go | Yes |
| DKIM on-device verification (Gmail) | Integration test: real `.eml` fixture | Yes |
| MTA attestation parsing (Outlook) | Unit test: Authentication-Results header samples | Yes |
| Forward rejection (8 languages) | Unit test: port forward detection tests | Yes |
| Confidence threshold filtering (< 0.7 excluded) | Unit test: verify filtering | Yes |
| Raw content not persisted | Unit test: assert SQLite has only claims | Yes |
| Scan progress indicator | Manual: connect Gmail, observe progress | No |
| Quota exhaustion (429) backoff + resume | Unit test: mock 429, assert backoff | Yes |
| Reconnect prompt on token refresh failure | Unit test: mock 401, assert state | Yes |
| Feature flag gating | Unit test: flag OFF → no routes, no polling | Yes |
| WorkManager polling | Instrumented test: verify worker fires | Yes |
| Module isolation (no wallet imports) | Build test: compile trusttrail/ independently | Yes |
| New platform suggestion | Unit test: unconsented platform triggers suggestion | Yes |
| Foundational identity gate | Unit test: no foundation → submission blocked | Yes |
| Evidence submission (claims only, no raw content) | Unit test: mock HTTP, assert payload shape + no raw content | Yes |
| Claim deselection excludes from bundle | Unit test: deselect claim → absent from submission | Yes |
| Disconnect provider deletes token + stops polling | Unit test: disconnect → KeyStore cleared, worker cancelled | Yes |
| DKIM failure → MTA fallback | Unit test: mock failed DKIM + valid AR header → mta_attested | Yes |
| DKIM failure → broken_signature rejection | Unit test: mock failed DKIM + no AR → rejected | Yes |
| Scan cursor persistence + resumption | Unit test: interrupt scan → resume from last timestamp | Yes |
