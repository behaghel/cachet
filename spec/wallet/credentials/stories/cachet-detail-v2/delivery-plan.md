---
story: cachet-detail-v2
status: approved
approved: 2026-05-09
wireframe: design/wireframes/cachet-01-detail-v2.svg
---

# Cachet Detail v2 — Delivery Plan

BDD+TDD delivery of the behavioral cachet detail screen with tier dial,
strength, and evidence breakdown.

## Prerequisites

Before starting delivery:

1. **Demo fixture for behavioral cachet** — the existing `DemoFixtures` only has
   identity/childcare/seller cachets. Need a "Trusted Host" behavioral cachet with:
   - strength: 0.72, tier: SILVER
   - evidence items from HomeExchange (7) and Vinted (3)
   - linked identity cachet
   - predicates: "Verified hosting track record", "Identity verified"

2. **OverlayScreen route** — add `OverlayScreen.BehavioralCachetDetail(detail)`
   to `WalletApp.kt` overlay sealed class.

3. **UI model** — `BehavioralCachetDetailUi` data class bridging credential
   data to screen state (separate from the existing `CachetDetailUi` which
   is for v1 identity cachets).

## Delivery Groups

### Group 1: Dial hero + name + back navigation (AC-1, AC-3, AC-9)

**BDD scenarios:**
- "Viewing a behavioral cachet shows the tier dial"
- "Cachet name is prominent below the tier"
- "Returning to vault"

**Step definitions to write:**
- `BehavioralCachetDetailSteps.kt` — new file
- Reuse `CommonSteps.iAmOnTheTab`, `CommonSteps.iPressBack`
- New: `iSeeACShapedCircularDial`, `theCachetShieldLogoIsCenteredInsideTheDial`,
  `theDialIsFilledInGreenUpToTheCurrentStrength`,
  `iSeeTheCachetNameBelowTheStrengthPercentage`

**TDD inner loop:**

| # | Test | Production code |
|---|------|----------------|
| 1 | `TierDialTest` — renders arc at given strength | `TierDial` composable (Canvas-based C-arc) |
| 2 | `BehavioralCachetDetailScreenTest` — screen contains dial + name | `BehavioralCachetDetailScreen` composable |
| 3 | `BehavioralCachetDetailUiTest` — maps credential to UI model | `BehavioralCachetDetailUi` data class + mapper |
| 4 | Navigation integration test | Add `OverlayScreen.BehavioralCachetDetail`, wire in `WalletApp.kt` |

**TierDial composable architecture:**
- `Canvas` with `drawArc` for the background track (grey) and progress fill (green)
- Arc spans ~270° (opening at bottom, matching logo C)
- Cachet shield logo rendered as `Image` centered inside
- Takes `strength: Float` (0.0–1.0) as parameter
- Test tag: `"tier_dial"`

**Demo fixture:**
- Add `BehavioralCachetDetailUi` to `DemoFixtures` with hardcoded Trusted Host data
- Register in `ScenarioRegistry` so BDD Background step can load it
- Wire: tapping a behavioral cachet card → `OverlayScreen.BehavioralCachetDetail`

**Visual verification:** Screenshot on S24 Ultra, compare to wireframe hero section.

---

### Group 2: Tier badge + strength display (AC-2, 3 variants)

**BDD scenario:** Scenario Outline with bronze/silver/gold

**Step definitions:**
- `theCachetStrengthIs(strength)` — configure demo fixture with given strength
- `iSeeTheTierBadgeShowing(tier)` — assert badge text matches
- `iSeeTheStrengthDisplayedAs(display)` — assert percentage text matches

**TDD inner loop:**

| # | Test | Production code |
|---|------|----------------|
| 1 | `TierBadgeTest` — renders correct tier name with metallic styling | `TierBadge` composable |
| 2 | `StrengthDisplayTest` — formats strength as percentage | strength formatting util |
| 3 | `TierBadgeVisibilityTest` — badge hidden when below bronze | conditional visibility |

**TierBadge composable:**
- Pill shape with gradient border (silver/gold/bronze gradient per tier)
- Dark inset with tier name in metallic-colored text
- Letter-spacing 1.5, weight 800
- Test tag: `"tier_badge"`
- Takes `tier: Tier?` — null = hidden (below bronze)

**Visual verification:** Three screenshots at different strengths.

---

### Group 3: Metadata + predicates (AC-4, AC-5)

**BDD scenarios:**
- "Metadata shows issuance and foundation link"
- "Predicates list what the cachet proves"

**Step definitions:**
- `iSeeTheIssuerAs(issuer)` — assert text "Cachet" (reuses existing step style)
- `iSeeTheLinkedIdentityCachetStatus` — assert "Identity ✓"
- `iSeeAWhatThisProvesSection` — assert section header
- `eachPredicateShowsACheckMarkAndDescription` — assert predicate rows
- `eachPredicateHasAPrivacyNote` — assert subtitle text on predicate rows

**TDD inner loop:**

| # | Test | Production code |
|---|------|----------------|
| 1 | `MetadataRowTest` — shows issued, issuer, foundation link | `MetadataRow` composable |
| 2 | `PredicatesSectionTest` — renders predicate list with checks + notes | `PredicatesSection` composable |

**Data model:**
- `BehavioralCachetDetailUi` includes:
  - `issuedAt: String` (formatted date)
  - `issuer: String` ("Cachet")
  - `foundationStatus: String` ("Identity ✓")
  - `predicates: List<PredicateUi>` (text + privacyNote)

---

### Group 4: Evidence breakdown (AC-6, AC-7)

**BDD scenarios:**
- "Evidence shows per-platform contribution" (multi-platform)
- "Evidence with a single platform" (100% contribution)

**Step definitions:**
- `theCachetHasEvidenceFromMultiplePlatforms` — use multi-platform demo fixture
- `theCachetHasEvidenceFromOnePlatformOnly` — use single-platform demo fixture
- `iSeeAnEvidenceSection` — assert "Evidence" header
- `eachPlatformShowsItsNameAndEvidenceItemCount` — assert text per row
- `eachPlatformShowsItsContributionPercentage` — assert % text
- `eachPlatformHasAProgressBar` — assert progress bar test tag
- `iSeeOnePlatformRowShowing100Contribution` — single row with 100%

**TDD inner loop:**

| # | Test | Production code |
|---|------|----------------|
| 1 | `EvidenceBreakdownTest` — renders platform rows with name, count, bar | `EvidenceBreakdown` composable |
| 2 | `PlatformContributionTest` — computes contribution % from evidence items | `PlatformContribution` util |
| 3 | `ContributionBarTest` — bar width proportional to percentage | `ContributionBar` composable |

**EvidenceBreakdown composable:**
- Section header "Evidence"
- List of `PlatformRow` cards
- Each row: platform name, "N evidence items", contribution %, mini progress bar
- Test tag per row: `"evidence_platform_${platform}"`

**PlatformContribution computation:**
- Input: `List<EvidenceItem>`, `DecayConfig`, current time
- Output: `List<PlatformContributionUi>` (name, count, contribution %)
- Uses `StrengthComputer.decayedScore` per item, groups by platform
- Contribution % = platform decayed sum / total decayed sum

---

### Group 5: Adaptive CTA + below-bronze edge case (AC-8, AC-10)

**BDD scenarios:**
- Scenario Outline: CTA text adapts to tier (bronze→silver, silver→gold, gold→fresh)
- "Cachet with strength below bronze" (no badge, 15%, CTA→bronze)

**Step definitions:**
- `theCachetTierIs(tier)` — configure demo fixture
- `iSeeASecondaryButtonWithText(text)` — assert outlined button text
- `theTierBadgeIsNotShown` — assert badge does not exist
- `theDialIsFilledTo(percent)` — assert dial fill level

**TDD inner loop:**

| # | Test | Production code |
|---|------|----------------|
| 1 | `TierCtaTextTest` — returns correct CTA text per tier | `tierCtaText(tier: Tier?)` function |
| 2 | `NoBadgeBelowBronzeTest` — badge hidden when tier is null | conditional rendering |
| 3 | `DialFillTest` — dial correctly renders at low fill levels | edge case in TierDial |

**CTA text logic:**
```kotlin
fun tierCtaText(tier: Tier?): String = when (tier) {
    null -> "Scan for more evidence to reach Bronze"
    Tier.BRONZE -> "Scan for more evidence to reach Silver"
    Tier.SILVER -> "Scan for more evidence to reach Gold"
    Tier.GOLD -> "Scan to keep your Gold status fresh"
}
```

---

## File Inventory (what gets created)

### Production code (androidApp)
```
android/trusttrail/ui/
  BehavioralCachetDetailScreen.kt    — main screen composable
  TierDial.kt                        — Canvas-based C-arc gauge
  TierBadge.kt                       — metallic pill badge
  MetadataRow.kt                     — issued/issuer/foundation row
  PredicatesSection.kt               — "What this proves" list
  EvidenceBreakdown.kt               — per-platform contribution cards
  ContributionBar.kt                 — mini progress bar

android/trusttrail/model/
  BehavioralCachetDetailUi.kt        — UI model for the screen

android/ui/fixtures/
  DemoFixtures.kt                    — add behavioral cachet fixtures (modify)

android/ui/WalletApp.kt              — add overlay route (modify)
android/ui/model/OverlayScreen.kt    — add sealed class entry (modify, if separate file)
```

### Test code
```
androidTest/bdd/steps/
  BehavioralCachetDetailSteps.kt     — BDD step definitions

test/ (unit tests)
  TierDialTest.kt
  TierBadgeTest.kt
  BehavioralCachetDetailUiTest.kt
  EvidenceBreakdownTest.kt
  PlatformContributionTest.kt
  TierCtaTextTest.kt
```

### Spec
```
spec/wallet/credentials/stories/cachet-detail-v2/
  scenarios.feature                  — already written
  delivery-plan.md                   — this file
```

## Ordering Rationale

1. **Group 1 first** — establishes the screen skeleton + navigation. Everything
   else builds on top of it. The dial is the highest-risk custom drawing.
2. **Group 2 next** — tier badge is the core UX signal. Scenario Outline tests
   three tiers in one pass.
3. **Group 3 before 4** — metadata and predicates are simpler and establish the
   information hierarchy before the evidence section.
4. **Group 4 before 5** — evidence breakdown is the novel component. Needs
   `PlatformContribution` computation.
5. **Group 5 last** — CTA text and edge cases. Polish layer.

## Notes for the implementing session

- The `TierDial` composable is the hardest part — Canvas `drawArc` with
  rounded stroke caps, ~270° sweep, opening at bottom. Start with a simple
  arc and iterate.
- The demo fixture should include BOTH a behavioral cachet and the existing
  identity/childcare cachets — the vault grid shows all of them, and tapping
  a behavioral one opens v2 detail while tapping identity opens v1.
- The `PlatformContribution` computation uses `StrengthComputer.decayedScore`
  from `trusttrail/strength/` — this is already tested (23 tests). Import it.
- All new composables go in `android/trusttrail/ui/` to maintain module isolation.
  They can import from `trusttrail/strength/` and `trusttrail/model/` (shared module)
  but NOT from `wallet/domain/`.
