# Screen Manifest

Maps each wireframe SVG to the screen, composable, and navigation steps in demo mode.

## Onboarding (launch WITHOUT demo_mode)

Launch: `adb shell am start -n id.cachet.wallet.android/.MainActivity`

| # | Wireframe | Screen | Nav Steps |
|---|-----------|--------|-----------|
| 1 | `holder-01-onboarding-1.svg` | Onboarding 1/3 | Fresh launch (no demo_mode) |
| 2 | `holder-02-onboarding-2.svg` | Onboarding 2/3 | Tap "Next" button |
| 3 | `holder-03-onboarding-3.svg` | Onboarding 3/3 | Tap "Next" button |

## Main Tabs (launch WITH demo_mode)

**Launch:** `adb shell am start -n id.cachet.wallet.android/.MainActivity --ez demo_mode true`

| # | Wireframe | Screen | Nav Steps |
|---|-----------|--------|-----------|
| 4 | `holder-04-vault-my-trust.svg` | Home / My Cachets | Direct after demo launch |
| 5 | `activity-01-tab.svg` | Activity | Tap "Activity" segment |

## Deeper Flows (reachable in demo mode)

| # | Wireframe | Screen | Nav Steps |
|---|-----------|--------|-----------|
| 6 | `holder-05-empty-vault.svg` | Empty Vault | `adb shell am start -n id.cachet.wallet.android/.MainActivity --ez demo_empty true` |
| 7 | `cachet-02-qr-share.svg` | QR Share | From My Cachets: tap any cachet card |
| 8 | `cachet-03-incoming-request.svg` | Incoming Request | Auto-transitions from QR Share after 4s (or tap "Scan simulated") |
| 9 | `cachet-04-result-pass.svg` | Cachet Result (pass) | Tap "Share & Cache" on Incoming Request |
| 10 | `cachet-05-result-fail.svg` | Cachet Result (fail) | Requires fail scenario data |

## Deprecated Wireframes

These wireframes are no longer canonical and should be skipped during reviews.

| Wireframe | Reason |
|-----------|--------|
| `home-a-split.svg` | Variant — not implemented |
| `home-b-credentials-fab.svg` | Variant — not implemented |
| `home-c-dual-tabs.svg` | Deprecated — replaced by My Cachets tab |
| `history-01-tab.svg` | Deprecated — merged into Activity tab |
| `holder-06-receipts.svg` | Deprecated — merged into Activity tab (Receipts filter) |
