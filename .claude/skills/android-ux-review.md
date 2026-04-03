---
name: android-ux-review
description: Build the Cachet wallet app in demo mode, navigate to every screen via Android MCP, capture screenshots, compare against approved wireframe SVGs, and output a structured fidelity report.
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
- Compare layout, content, typography, color usage, and interactive elements
- Tap "Next" to advance to the next onboarding page
- After capturing all 3, tap "Skip" or proceed to complete onboarding

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
- Compare and note differences
- Scroll down if needed to check below-fold content

### 5. Compile report

Output a markdown report with this structure:

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
| Home / My Trust | holder-04-vault-my-trust.svg | Minor | Status badge says "Passed" instead of "Verified" |
| ... | ... | ... | ... |

### Detailed Findings
[For each non-Match screen, list specific differences]

### Screens Not Reviewed
[List any screens from the manifest that couldn't be reached]
```

## Verdicts

- **Match** — Screen faithfully implements the wireframe
- **Minor** — Small deviations that don't affect UX intent (e.g., slightly different spacing)
- **Gap** — Missing elements, wrong content, or structural differences from the wireframe
- **Upgrade** — Intentional improvement over the wireframe (e.g., badge icons instead of emoji)

## Tips

- The emulator screen is small (320x640). Scroll to check below-fold content.
- Use `mcp__android__get_uilayout` to find tap coordinates for buttons.
- Wireframe SVGs contain HTML comments with screen descriptions — read them for context.
- Demo fixtures in `ui/fixtures/DemoFixtures.kt` define the expected data for each screen.
