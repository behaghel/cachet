package id.cachet.wallet.android.ui.fixtures

/** Prod stub: scenario registry is not available in production builds. */
object ScenarioRegistry {
    fun get(name: String): Nothing = error("Demo scenarios not available in production")
    fun all(): Nothing = error("Demo scenarios not available in production")
}
