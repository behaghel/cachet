# Screen Manifest

Maps each wireframe SVG to the screen, composable, and navigation steps in demo mode.

**Launch command:** `adb shell am start -n id.cachet.wallet.android/.MainActivity --ez demo_mode true`

## Onboarding (launch WITHOUT demo_mode)

Launch: `adb shell am start -n id.cachet.wallet.android/.MainActivity`

| # | Wireframe | Screen | Nav Steps |
|---|-----------|--------|-----------|
| 1 | `holder-01-onboarding-1.svg` | Onboarding 1/3 | Fresh launch (no demo_mode) |
| 2 | `holder-02-onboarding-2.svg` | Onboarding 2/3 | Tap "Next" button |
| 3 | `holder-03-onboarding-3.svg` | Onboarding 3/3 | Tap "Next" button |

## Main Tabs (launch WITH demo_mode)

| # | Wireframe | Screen | Nav Steps |
|---|-----------|--------|-----------|
| 4 | `holder-04-vault-my-trust.svg` | Home / My Trust | Direct after demo launch |
| 5 | `home-c-dual-tabs.svg` | Home / Cache it | Tap "Cache it" segment |
| 6 | `history-01-tab.svg` | History | Tap "History" in bottom nav |
| 7 | `holder-06-receipts.svg` | Receipts | Tap "Receipts" in bottom nav |

## Deeper Flows (not reachable from demo nav yet)

| # | Wireframe | Screen | Notes |
|---|-----------|--------|-------|
| 8 | `holder-05-empty-vault.svg` | Empty Vault | Requires WalletUiState.Empty |
| 9 | `cachet-02-qr-share.svg` | QR Share | Requires cach'pack card tap → overlay |
| 10 | `cachet-03-incoming-request.svg` | Incoming Request | Requires QR scan → overlay |
| 11 | `cachet-04-result-pass.svg` | Cachet Result (pass) | Requires "Share & Cache" → overlay |
| 12 | `cachet-05-result-fail.svg` | Cachet Result (fail) | Requires fail scenario → overlay |

## Variant Wireframes (explored but not chosen)

| Wireframe | Notes |
|-----------|-------|
| `home-a-split.svg` | Split home variant — not implemented |
| `home-b-credentials-fab.svg` | Credentials+FAB variant — not implemented |
| `activity-01-tab.svg` | Activity tab variant — History tab is a hybrid |
