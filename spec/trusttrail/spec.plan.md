---
domain: trusttrail
spec: spec/trusttrail/spec.md
status: complete
approved: 2026-05-03
---

# TrustTrail — TDD Iteration Plan

7 vertical slices, ordered by dependency. Each slice is end-to-end and
user-exercisable on emulator.

## Iteration Table

| # | Slice | Goal | Criteria |
|---|-------|------|----------|
| 1 | ~~Extraction engine + demo screen~~ | ~~Kotlin port of Go extraction, demo TrustTrail screen with fixture claims~~ | ~~4.1, 4.5, 4.6, 4.7, 8.1, 8.2~~ DONE |
| 2 | ~~Gmail OAuth + platform discovery~~ | ~~Connect Gmail, scan headers, show discovered platforms~~ | ~~1.1–1.4, 2.1–2.3~~ DONE |
| 3 | ~~Platform consent + full extraction~~ | ~~Select platforms, fetch full MIME, extract and persist claims~~ | ~~3.1, 3.2, 4.1 (real), 7.2~~ DONE |
| 4 | ~~DKIM verification + trust levels~~ | ~~On-device DKIM for Gmail, MTA attestation for Outlook, failure fallback~~ | ~~4.2, 4.3, 4.4, 4.4b, 4.5~~ DONE |
| 5 | ~~Evidence review + submission~~ | ~~Review claims, deselect, submit bundle to issuance gateway~~ | ~~5.1–5.4, 7.1~~ DONE |
| 6 | ~~Scan resilience~~ | ~~Cursor persistence, resumption, quota backoff, disconnect~~ | ~~2.4, 2.5, 2.6, 1.5~~ DONE |
| 7 | ~~Continuous monitoring~~ | ~~WorkManager polling, new platform suggestions, offline retry~~ | ~~3.3, 6.1–6.3~~ DONE |

---

## Slice 1: Extraction engine + demo screen

**Goal:** User navigates to TrustTrail (feature flag ON), sees claims extracted
from hardcoded fixture emails in demo mode.

**User interaction:** Flag ON → open TrustTrail tab → see a list of claims
extracted from bundled test emails (Vinted sale, HomeExchange booking, Care.com
receipt).

**Tests to write first:**
- `PlatformDetectionTest` — port all platform domain matching from Go
  (care.com, sittercity, urbansitter, vinted.*, homeexchange.com)
- `ClaimExtractionTest` — port 20+ pattern extraction tests
  (subject + body, named captures)
- `ForwardDetectionTest` — 8 forward prefixes + 3 body markers
- `ConfidenceFilterTest` — claims below 0.7 excluded
- `FeatureFlagTest` — flag OFF → no TrustTrail route, no DI registration
- `ModuleIsolationTest` — trusttrail/ compiles with zero wallet imports

**Expected red:** `PlatformDetectionTest` fails — no Kotlin extraction code exists.

**Minimal green:** Port `patterns.go` and `extract.go` to Kotlin. Create module
skeleton: Koin module, feature flag, navigation route, basic Compose screen that
runs extraction on bundled `.eml` fixtures and displays results.

**Feedback checkpoint:** Screenshot the demo TrustTrail screen on emulator.
Ask: "Does the claim display format look right for the next slice?"

---

## Slice 2: Gmail OAuth + platform discovery

**Goal:** User connects their Gmail account via Google Sign-In, the scanner
fetches headers (From, Subject, Date) for the last 6 months, and shows which
known platforms were found.

**User interaction:** Tap "Connect Email" → provider picker → Google consent
screen → headers scan → "Discovered: Vinted (12), HomeExchange (3)".

**Tests to write first:**
- `GmailOAuthScopeTest` — assert scope is `gmail.readonly`, no other
- `TokenStorageTest` — token stored in Android Keystore, not SharedPrefs
- `TokenNeverSentTest` — mock HTTP client, assert no outbound call with token
- `HeadersOnlyFetchTest` — mock Gmail client, assert `format=METADATA`
- `PlatformDiscoveryTest` — headers with vinted.es From → "Vinted" in list
- `UnknownSenderIgnoredTest` — unknown From domain → not in list, body never fetched

**Expected red:** `GmailOAuthScopeTest` fails — no OAuth code exists.

**Minimal green:** Add Google Sign-In SDK dependency, implement
`GmailEmailProvider` (headers-only fetch), create
`InboxScannerUseCase.discoverPlatforms()`, wire up provider picker + discovered
platforms list UI.

**Feedback checkpoint:** Screen recording of OAuth flow on emulator.
Ask: "Is the platform discovery UX clear enough?"

---

## Slice 3: Platform consent + full extraction

**Goal:** User selects which discovered platforms to process. Full MIME content
is fetched only for consented platforms. Claims are extracted and persisted to
SQLite.

**User interaction:** Check "Vinted" → tap "Process Selected" → progress →
evidence summary showing real claims grouped by platform.

**Tests to write first:**
- `SelectiveConsentTest` — consented → full fetch; unconsented → never fetched
- `FullMimeFetchTest` — mock Gmail, assert `format=RAW` only for consented
- `ClaimPersistenceTest` — claims in SQLite, raw email NOT in SQLite
- `EvidenceSummaryTest` — claims grouped by platform with type/date/confidence

**Expected red:** `SelectiveConsentTest` fails — no consent flow exists.

**Minimal green:** SQLDelight migration (claims table, scan state table),
`TrustTrailRepository`, consent UI with checkboxes, full MIME fetch for
consented platforms, evidence summary Compose screen.

**Feedback checkpoint:** Screenshot evidence summary with real claims on emulator.
Ask: "Are the claim types and fields readable?"

---

## Slice 4: DKIM verification + trust levels

**Goal:** Claims carry a verified trust level. Gmail = on-device DKIM
(`cryptographic`). DKIM failure falls back to MTA attestation. Outlook = MTA
attestation directly.

**User interaction:** Evidence summary shows trust badge per claim — green
shield for `cryptographic`, grey shield for `mta_attested`. Broken-signature
emails are filtered with count.

**Tests to write first:**
- `DkimVerificationSuccessTest` — valid DKIM → "cryptographic"
- `DkimFailureMtaFallbackTest` — DKIM fails + AR `dkim=pass` → "mta_attested"
- `DkimFailureNoFallbackTest` — DKIM fails + no AR → rejected "broken_signature"
- `MtaAttestationTest` — Outlook, AR `dkim=pass` → "mta_attested"
- `MtaAttestationFailureTest` — Outlook, no AR pass → skipped

**Expected red:** `DkimVerificationSuccessTest` fails — no Kotlin DKIM verifier.

**Minimal green:** Port DKIM verification to Kotlin (or wrap JVM library),
implement `AuthenticationResultsParser`, wire trust level into extraction
pipeline and UI.

**Feedback checkpoint:** Screenshot with trust level badges on emulator.
Ask: "Is the visual distinction between cryptographic and mta_attested clear?"

---

## Slice 5: Evidence review + submission

**Goal:** User reviews claims, deselects some, submits evidence bundle to
issuance gateway. Foundational identity required.

**User interaction:** Review screen with per-claim checkboxes → deselect → tap
"Submit Evidence" → bundle sent → confirmation. Without foundational cachet:
blocked with prompt.

**Tests to write first:**
- `ClaimDeselectionTest` — deselected claim absent from bundle
- `EvidenceSubmissionPayloadTest` — mock HTTP, assert payload shape (type,
  fields, confidence, trust_level, platform, date, DKIM proof; no raw email)
- `FoundationalIdentityGateTest` — no cachet → submission blocked
- `SubmissionOnlyBackendCallTest` — this is the only HTTP call to cachet backend

**Expected red:** `EvidenceSubmissionPayloadTest` fails — no submission code.

**Minimal green:** `EvidenceSubmissionUseCase`, evidence bundle DTO, HTTP client
for issuance gateway, review screen with deselection, foundational identity
check.

**Feedback checkpoint:** Show evidence bundle JSON payload.
Ask: "Does this payload shape work for the issuance gateway?"

---

## Slice 6: Scan resilience

**Goal:** Scan survives interruptions. Progress visible. Quota exhaustion
handled. Disconnect works.

**User interaction:** Cold-start scan → progress bar (342/1,200) → kill app →
reopen → resumes from 342. Quota hit → "Scan paused" → auto-resumes.
Disconnect → token gone, polling stops.

**Tests to write first:**
- `ScanCursorPersistenceTest` — interrupted → cursor timestamp saved → resume
- `ScanResumptionTest` — after resume, no re-fetch of scanned messages
- `QuotaExhaustionTest` — mock 429 → exponential backoff → resume from cursor
- `DisconnectProviderTest` — disconnect → KeyStore cleared, worker cancelled
- `ScanProgressTest` — progress state exposes scanned/total counts

**Expected red:** `ScanCursorPersistenceTest` fails — no cursor persistence.

**Minimal green:** Add `scan_cursor` to scan state table, implement cursor
save/resume, 429 detection with backoff, progress state in UI, disconnect flow.

**Feedback checkpoint:** Screen recording: scan progress → kill → resume.
Ask: "Is per-provider progress needed, or is aggregate sufficient?"

---

## Slice 7: Continuous monitoring

**Goal:** Background polling detects new emails, suggests new platforms, retries
failed submissions on connectivity restore.

**User interaction:** New Vinted email → poll cycle → notification "1 new claim".
New Care.com email → suggestion "Process Care.com?". Offline → reconnect →
pending submission retried.

**Tests to write first:**
- `WorkManagerSchedulingTest` — worker scheduled with >= 15 min interval
- `NewEmailDetectionTest` — email newer than cursor → extracted automatically
- `NewPlatformSuggestionTest` — unconsented platform → suggestion state emitted
- `OfflineResilienceTest` — pending submission queued → connectivity → retried

**Expected red:** `WorkManagerSchedulingTest` fails — no WorkManager integration.

**Minimal green:** `TrustTrailPollingWorker` (PeriodicWorkRequest), notification
for new claims, suggestion state in ViewModel, `EvidenceQueue` with retry.

**Feedback checkpoint:** Emulator: trigger poll, verify new claim detected.
Ask: "Is notification useful, or would in-app badge be better?"

---

## Ordering Rationale

1. **Slice 1 first** — extraction engine is the foundation and highest-risk
   (porting 20+ regex patterns). Demo mode validates without network code.
2. **Slice 2 next** — OAuth is the gateway; nothing real works without inbox
   access.
3. **Slice 3 before 4** — need actual extracted claims before verifying DKIM.
   Consent is the user-facing gate.
4. **Slice 4 before 5** — trust levels are part of the evidence bundle.
5. **Slice 5 before 6** — submission is the core value delivery.
6. **Slice 6 before 7** — resilience needed for cold-start (main use case).
7. **Slice 7 last** — polish layer; everything works without it.
