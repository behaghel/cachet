package id.cachet.wallet.android.ui.fixtures

/** Lookup table for demo scenarios. Selectable via `--es demo_scenario <name>`. */
object ScenarioRegistry {
    private val scenarios: Map<String, DemoScenario> = mapOf(
        "happy" to HappyPathScenario,
        "empty" to EmptyVaultScenario,
        "revoked" to RevokedScenario,
        "expired" to ExpiredScenario,
        "seller-only" to SellerOnlyScenario,
    )

    fun get(name: String): DemoScenario = scenarios[name] ?: HappyPathScenario

    fun all(): Map<String, DemoScenario> = scenarios
}
