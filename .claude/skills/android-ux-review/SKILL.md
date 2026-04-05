---
name: android-ux-review
description: Build the Cachet wallet app in demo mode, navigate to every screen via Android MCP, capture screenshots, compare element-by-element against approved wireframe SVGs, and output a structured fidelity report.
user_invocable: true
---

# /android-ux-review

You are reviewing the Android wallet app's UX fidelity against approved wireframes.

## Prerequisites

- Android emulator running (check with `adb devices`)
- Android MCP server connected (mcp__android__* tools available)
- Backend services NOT required (demo mode uses fixture data)

## Steps

### 1. Build and install

```bash
devenv shell -- android:install
```

### 2. Read the screen manifest

Read `design/wireframes/MANIFEST.md` to get the ordered list of screens, their wireframe SVGs, and navigation steps.

### 3. Capture onboarding screens (without demo_mode)

Launch the app normally (no `--ez demo_mode true`). For each onboarding wireframe in the manifest:
- Use `mcp__android__get_screenshot` to capture the current screen
- Read the corresponding wireframe SVG from `design/wireframes/`
- Run the **element-by-element checklist** (see below)
- Tap "Next" to advance to the next onboarding page
- After capturing all 4, tap "Skip" or proceed to complete onboarding

### 4. Capture main tab screens (with demo_mode)

Force-stop and relaunch in demo mode:
```
adb shell am force-stop id.cachet.wallet.android
adb shell am start -n id.cachet.wallet.android/.MainActivity --ez demo_mode true
```

For each main tab wireframe in the manifest:
- Navigate using tap commands (use `mcp__android__get_uilayout` to find button coordinates)
- Capture screenshot
- Read the matching wireframe SVG
- Run the **element-by-element checklist** (see below)
- Scroll down if needed to check below-fold content

### 5. Capture deeper flow screens

Follow the navigation steps in the manifest to reach overlay flows:
- Empty Vault: `adb shell am start -n id.cachet.wallet.android/.MainActivity --ez demo_empty true`
- QR Share: tap a cachet card from My Cachets
- Incoming Request: auto-transitions from QR Share after 4s
- Cachet Result: tap "Verify & Share" on Incoming Request

### 6. Compile report

Output a markdown report with the structure shown in the Report Template section.

## Element-by-Element Checklist

For EVERY screen, systematically compare these aspects against the wireframe SVG. Do NOT rely on overall impression — check each point explicitly.

### A. Layout order (top to bottom)
List every element in the wireframe from top to bottom, then verify the app renders them in the **exact same order**. Flag any swapped, missing, or extra elements. This is a common source of gaps (e.g., CTA button above vs below a card).

### B. Component types
For each visual element in the wireframe, verify the app uses the **correct component type**:
- Does the wireframe use a shield cachet icon? → App must use `CachetMark`, not a colored circle or emoji
- Does the wireframe use a brand shield? → App must use `BrandShieldMark`, not circles/arcs
- Does the wireframe use predicate chips? → App must use pill-shaped chips, not plain text
- Does the wireframe show direction arrows? → App must show `DirectionIndicator`

### C. Text content
Compare every text string: titles, subtitles, button labels, status labels, card content. Flag any vocabulary mismatches (e.g., "credentials" vs "cachets").

### D. Visual alignment
- Is each element horizontally centered when the wireframe shows it centered?
- Are elements properly aligned to the left/right edges as shown?
- Use `uiautomator dump` to check actual pixel bounds if centering looks off.

### E. Spacing and proportions
- Is the relative spacing between elements roughly correct?
- Are elements given appropriate visual weight (size)?
- On the 320x640 emulator, verify elements aren't clipped or overflowing.

### F. Colors and states
- Do trust status chips use the correct color scheme (green=verified, amber=pending, red=revoked)?
- Are dark/light surface colors correct for the screen context?

### G. Interactive elements
- Are tappable elements actually clickable? (verify with `mcp__android__get_uilayout`)
- Do navigation flows work as described in the manifest?

## Known Pitfalls

These are issues that have been caught before. Always verify they haven't regressed:

1. **Canvas centering**: Any `DrawScope.scale()` call MUST use `pivot = Offset.Zero`. Without it, SVG path drawings shift rightward. Check every shield/logo on screen.
2. **Vocabulary drift**: The brand uses "cachets", never "credentials" or "badges". Check every text string.
3. **Component substitution**: Wireframes use specific shield icons (`<use href="#childcare-shield">` etc). The app must use `CachetMark(type=...)`, not colored circles or emoji as placeholder.
4. **Layout order**: The wireframe's vertical element order is authoritative. Don't assume CTA buttons go at the bottom — read the wireframe SVG coordinates.

## Report Template

```markdown
## UX Fidelity Report — [date]

### Summary
- Screens reviewed: X/Y
- Matches: N
- Minor issues: N
- Gaps: N

### Per-Screen Results

| Screen | Wireframe | Verdict | Notes |
|--------|-----------|---------|-------|
| Onboarding 1 | holder-01-onboarding-1.svg | Match | — |
| Home / My Cachets | holder-04-vault-my-trust.svg | Minor | ... |
| ... | ... | ... | ... |

### Detailed Findings

For each non-Match screen:

**[Screen Name] — [Verdict]**
- Layout order: [any ordering issues]
- Component types: [any wrong component types]
- Text content: [any wording mismatches]
- Alignment: [any centering/positioning issues]
- Missing elements: [anything in wireframe but not in app]
- Extra elements: [anything in app but not in wireframe]

### Screens Not Reviewed
[List any screens from the manifest that couldn't be reached, with reason]
```

## Verdicts

- **Match** — Screen faithfully implements the wireframe across all checklist dimensions
- **Minor** — Small deviations in spacing or styling that don't affect UX intent
- **Gap** — Wrong component types, wrong layout order, missing elements, wrong text, or broken alignment
- **Upgrade** — Intentional improvement over the wireframe (document why it's better)

## Tips

- The emulator screen is small (320x640). Scroll to check below-fold content.
- Use `mcp__android__get_uilayout` to find tap coordinates for buttons.
- Use `uiautomator dump` to verify exact pixel bounds when alignment looks wrong.
- Wireframe SVGs contain HTML comments with screen descriptions — read them for context.
- Demo fixtures in `ui/fixtures/DemoFixtures.kt` define the expected data for each screen.
- Read the wireframe SVG element coordinates to determine intended layout order — don't assume.
