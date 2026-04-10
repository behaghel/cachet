# Plan: Scenario-Driven Demo Mode

## Context

Demo mode has a confirmed bug: selecting any cach'pack in HOLDER mode always produces an Identity Credential because the selected pack is discarded at `WalletApp.kt:83` and `startVeriffVerification()` is a no-op in demo mode (`WalletViewModel.kt:354`). Beyond the bug, demo mode is a monolithic `DemoFixtures` object with static vals that can't surface edge cases (revoked, expired, different pack results). The `/android-ux-review` skill only exercises the happy path. There is no compile-time guarantee that demo code stays out of release builds.

This plan fixes the bug, introduces scenario-driven demo state, adds missing wireframes, refactors the UX review skill, enforces demo/prod isolation, and sequences the rebase of open PRs #85/#86/#87.

---

## Slice 1 — Fix the bug (pack-aware demo flow)

**Branch:** `fix/pack-selection-demo`

**The bug chain:**
1. `WalletApp.kt:83-85` — HOLDER `onPackSelected` discards `pack`, calls `startVeriffVerification()` with no args
2. `WalletViewModel.kt:354` — `startVeriffVerification()` returns immediately in demo mode
3. `WalletViewModel.kt:414-424` — `shareCredential()` always returns `DemoFixtures.cachetResultPass` (hardcoded "Childcare Ready")

**Fix — reuse `CachPackMapper` which already exists and does the right thing:**

### `WalletApp.kt`
- **Line 83-85** — HOLDER `onPackSelected`: instead of `viewModel.startVeriffVerification()`, transition straight to the consent screen:
  ```kotlin
  PackPickerMode.HOLDER -> {
      overlay = OverlayScreen.IncomingRequest(
          CachPackMapper.toVerificationRequest(pack)
      )
  }
  ```
- **Line 220** — QR scanner demo fallback: replace `DemoFixtures.childcareRequest` with `CachPackMapper.toVerificationRequest(DemoFixtures.cachPacks.first())` (deterministic but uses the mapper)
- **Line 338-343** — `defaultPackIdForType()`: fix `AGE → "pack.age.check"` and `IDENTITY → "pack.identity.verification"` (currently both map to childcare pack IDs)

### `WalletViewModel.kt`
- **Line 414-424** — `shareCredential()` demo branch: replace hardcoded fixtures with:
  ```kotlin
  val matchingPack = DemoFixtures.packForType(request.cachetType ?: CachetType.IDENTITY)
  val allPassed = !request.question.contains("seller", ignoreCase = true)
  return CachPackMapper.toCachetResult(matchingPack, allPassed)
  ```
  Keep consent receipt generation unchanged.

### `DemoFixtures.kt`
- Add thin lookup: `fun packForType(type: CachetType): CachPackUi = cachPacks.first { it.cachetType == type }`
- Deprecate `cachetResultPass` / `cachetResultFail` with `@Deprecated` pointing to `CachPackMapper.toCachetResult()`

### Verify
- `devenv shell -- android:test-unit`
- Install on emulator, launch `--ez demo_mode true`:
  - Pick Childcare → Incoming Request shows childcare predicates → Result says "Childcare Ready"
  - Pick Age → Result says "Age Verified", 1/1 proofs
  - Pick Seller → Result says "Incomplete", 0/4 proofs
- Screenshot each result screen

---

## Slice 2 — DemoScenario abstraction + scenario registry

**Branch:** `refactor/demo-scenario-architecture`

### New files in `ui/fixtures/`

**`DemoScenario.kt`** — sealed interface:
```kotlin
sealed interface DemoScenario {
    val name: String
    val credentials: List<CredentialCardUi>
    val vaultSummary: VaultSummaryUi
    val cachPacks: List<CachPackUi>
    val historyGroups: List<HistoryGroup>
    val receipts: List<ReceiptItem>
    val cachetDetails: Map<String, CachetDetailUi>
    val defaultScanPack: CachPackUi
    fun shouldPass(request: VerificationRequest): Boolean =
        !request.question.contains("seller", ignoreCase = true)
}
```

**`HappyPathScenario.kt`** — extracts all current `DemoFixtures` vals (3 credentials, 3 packs, history, receipts, details). Object singleton implementing `DemoScenario`.

**`RevokedScenario.kt`** — 3 credentials, identity is revoked (`isRevoked=true`, `trustStatus=REVOKED`). Detail for identity shows "Revoked on Apr 5, 2026" metadata row. Share button disabled.

**`ExpiredScenario.kt`** — identity credential has expired date in past, `freshnessLabel="Expired"`.

**`SellerOnlyScenario.kt`** — single seller credential in PENDING state. Forces seller-fail verification path.

**`ScenarioRegistry.kt`**:
```kotlin
object ScenarioRegistry {
    private val scenarios = mapOf(
        "happy" to HappyPathScenario,
        "empty" to EmptyVaultScenario,
        "revoked" to RevokedScenario,
        "expired" to ExpiredScenario,
        "seller-only" to SellerOnlyScenario,
    )
    fun get(name: String): DemoScenario = scenarios[name] ?: HappyPathScenario
    fun all(): Map<String, DemoScenario> = scenarios
}
```

### Modified files

**`MainActivity.kt`** — read `demo_scenario` string extra, pass through to `WalletApp`.

**`WalletApp.kt`** — add `demoScenario: String = ""` param. Resolve via `ScenarioRegistry.get()`. Pass scenario to ViewModel.

**`WalletViewModel.kt`** — accept `DemoScenario` instead of bare booleans. `loadDemoCredentials()` reads from scenario. `shareCredential()` calls `scenario.shouldPass(request)`.

**`DemoFixtures.kt`** — thin delegation: `val credentials get() = activeScenario.credentials` etc. Keeps backward compat for existing callers.

### Verify
- `adb ... --ez demo_mode true --es demo_scenario revoked` → vault shows revoked identity
- `adb ... --ez demo_mode true --es demo_scenario expired` → vault shows expired credential
- `adb ... --ez demo_mode true` (no scenario) → happy path unchanged
- `devenv shell -- android:test-unit`

---

## Slice 3 — New wireframes for missing states

**Branch:** `design/missing-wireframes` (can merge in parallel with code slices)

### New SVGs in `design/wireframes/`

| File | State | Scenario |
|------|-------|----------|
| `cachet-01-detail-revoked.svg` | Revoked detail: red REVOKED chip, disabled Share, "Revoked on" row, no revoke link | `revoked` |
| `cachet-01-detail-hardware.svg` | Hardware-secured indicator: shield icon + "Hardware-secured" label below status chip | `happy` (with keyAlias) |
| `cachet-04-result-pass-age.svg` | Age Verified result: AGE shield, 1/1 proofs, "30 days" validity | `happy` (age pack) |
| `cachet-05-result-fail-seller.svg` | Seller fail result: SELLER shield, "Incomplete", 2/4 proofs, seller predicates | `seller-only` |
| `holder-04-vault-revoked.svg` | My Cachets grid with one revoked card (red border/overlay) | `revoked` |

### Update `design/wireframes/MANIFEST.md`

Add new section:

```markdown
## Scenario-Specific Screens

Launch with: `adb shell am start -n id.cachet.wallet.android/.MainActivity --ez demo_mode true --es demo_scenario <name>`

| # | Wireframe | Screen | Scenario | Nav Steps |
|---|-----------|--------|----------|-----------|
| S1 | cachet-01-detail-revoked.svg | Revoked Detail | revoked | Tap revoked card |
| S2 | cachet-01-detail-hardware.svg | Hardware-Secured Detail | happy | Tap identity card |
| S3 | cachet-04-result-pass-age.svg | Age Result (pass) | happy | Pick Pack → Age → Verify |
| S4 | cachet-05-result-fail-seller.svg | Seller Result (fail) | seller-only | Pick Pack → Seller → Verify |
| S5 | holder-04-vault-revoked.svg | Vault with Revoked | revoked | Direct after launch |
```

---

## Slice 4 — Refactor `/android-ux-review` skill

**Branch:** `refactor/ux-review-scenarios`

### `.claude/skills/android-ux-review/SKILL.md`

**New Step 3b** after main tab capture — "Scenario walkthrough":

For each scenario in `[happy, revoked, expired, seller-only]`:
1. `adb shell am force-stop id.cachet.wallet.android`
2. `adb shell am start -n id.cachet.wallet.android/.MainActivity --ez demo_mode true --es demo_scenario <name>`
3. Capture + compare scenario-specific screens listed in MANIFEST.md § Scenario-Specific Screens
4. Navigate deeper flows reachable from that scenario state

**Updated Report Template** — add "Scenario" column:

```markdown
| Screen | Wireframe | Scenario | Verdict | Notes |
```

**New Known Pitfall**:
- "Pack selection: verify that selecting AGE pack in HOLDER mode shows age predicates on IncomingRequest, not childcare predicates."

**Updated nav steps for result screens**:
- Clarify that `cachet-04-result-pass.svg` shows the CHILDCARE scenario result
- `cachet-04-result-pass-age.svg` shows the AGE scenario result
- `cachet-05-result-fail.svg` is now `cachet-05-result-fail-seller.svg` with seller predicates

---

## Slice 5 — Demo/prod compile-time isolation

**Branch:** `refactor/demo-prod-isolation`

### Approach: Gradle productFlavors

Two flavors (`demo`, `prod`) × two build types (`debug`, `release`). Only build `demoDebug` for development/QA and `prodRelease` for production.

### `mobile/androidApp/build.gradle.kts`

```kotlin
flavorDimensions += "mode"
productFlavors {
    create("demo") {
        dimension = "mode"
        applicationIdSuffix = ".demo"
        buildConfigField("boolean", "DEMO_ENABLED", "true")
    }
    create("prod") {
        dimension = "mode"
        buildConfigField("boolean", "DEMO_ENABLED", "false")
    }
}
```

### Source set moves

| Current location (src/main/) | Move to |
|------------------------------|---------|
| `ui/fixtures/DemoFixtures.kt` | `src/demo/kotlin/.../ui/fixtures/` |
| `ui/fixtures/DemoScenario.kt` + all scenarios | `src/demo/kotlin/.../ui/fixtures/` |
| `verification/MockVeriffService.kt` | `src/demo/kotlin/.../verification/` |
| `config/AppConfig.DEV_WEBHOOK_SECRET` | `src/demo/kotlin/.../config/DemoConfig.kt` |

### Prod stubs in `src/prod/kotlin/`

- `DemoFixtures.kt` — object with properties that throw `error("Demo not available in production")`
- `NoOpVeriffService.kt` — returns `VeriffResult.Cancelled` (placeholder until real SDK integration)

### `AndroidModule.kt`

```kotlin
single<VeriffService> {
    if (BuildConfig.DEMO_ENABLED) MockVeriffService()
    else NoOpVeriffService()
}
```

### Build command updates in `devenv.nix`

- `android:build` → `./gradlew assembleDemoDebug`
- `android:install` → `./gradlew installDemoDebug`
- Add `android:build-release` → `./gradlew assembleProdRelease`

### Verify
- `./gradlew assembleDemoDebug` succeeds, APK contains DemoFixtures
- `./gradlew assembleProdRelease` succeeds, APK does NOT contain DemoFixtures (verify with `apkanalyzer dex packages`)
- `grep -r "dev-secret" mobile/androidApp/src/main/` → no results
- All unit tests pass (run against demo flavor)

---

## Slice 6 — PR rebase strategy

**Merge order** (each step waits for the previous to land on main):

```
main ← Slice 1 (bug fix)
     ← PR #85 rebase (hardware + revocation detail) — clean, touches CachetDetailScreen only
     ← PR #87 rebase (SD-JWT disclosures) — clean, touches CachPackMapper/RequestPredicate
     ← PR #86 rebase (relay polling UX) — conflicts in WalletApp.kt overlay handling, manual resolution
     ← Slice 2 (scenario architecture)
     ← Slice 3 (wireframes, can merge in parallel with Slice 2)
     ← Slice 4 (UX review skill, depends on Slice 2 + 3)
     ← Slice 5 (demo/prod isolation, last — most invasive)
```

**Conflict forecast:**

| PR/Slice | Conflict risk | Zone |
|----------|--------------|------|
| #85 after Slice 1 | Low | Different files (CachetDetailScreen vs WalletApp/ViewModel) |
| #87 after #85 | Low | Adds DisclosureType to RequestPredicate — orthogonal |
| #86 after #87 | **High** | QrShareScreen + WalletApp overlay state machine |
| Slice 2 after #86 | Medium | WalletViewModel init + DemoFixtures refactor |

**Rule:** Never branch off an in-flight PR. Each branch starts from main after the previous merges.

---

## Files touched (summary)

| Slice | Create | Modify |
|-------|--------|--------|
| 1 (bug fix) | — | WalletApp.kt, WalletViewModel.kt, DemoFixtures.kt |
| 2 (scenarios) | DemoScenario.kt, HappyPathScenario.kt, RevokedScenario.kt, ExpiredScenario.kt, SellerOnlyScenario.kt, ScenarioRegistry.kt | MainActivity.kt, WalletApp.kt, WalletViewModel.kt, DemoFixtures.kt |
| 3 (wireframes) | 5 new SVGs | MANIFEST.md |
| 4 (UX skill) | — | SKILL.md |
| 5 (isolation) | DemoConfig.kt, NoOpVeriffService.kt, prod stubs | build.gradle.kts, AndroidModule.kt, devenv.nix; move 6+ files to src/demo/ |
