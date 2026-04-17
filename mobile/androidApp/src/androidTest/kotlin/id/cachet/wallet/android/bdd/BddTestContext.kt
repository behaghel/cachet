package id.cachet.wallet.android.bdd

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import id.cachet.wallet.android.MainActivity
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.fixtures.ScenarioRegistry

/**
 * Shared test context for all Cucumber step definitions.
 *
 * Holds a single composeTestRule so that all step classes interact with
 * the same activity and Compose tree. The rule is created once and stored
 * in the companion object; step classes access it via [rule].
 *
 * Scenario selection: Background steps set [pendingScenario] before any
 * UI interaction. The first UI step calls [ensureLaunched] which creates
 * the activity with the right intent. For the default "happy" scenario,
 * the rule launches immediately. For other scenarios, the activity is
 * recreated with the correct DemoFixtures.
 */
class BddTestContext {

    companion object {
        @Volatile
        var sharedRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>? = null

        /**
         * After tapping "Verify & Share", handles the liveness gate if it appears,
         * then waits for the verification result screen.
         *
         * High-value packs (Childcare, Seller, Identity) show a liveness screen;
         * low-value packs (Age) skip straight to the result.
         */
        fun passLivenessIfNeeded(
            rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
        ) {
            rule.waitForIdle()
            // Wait for either liveness or result screen to appear
            rule.waitUntil(timeoutMillis = 10000) {
                rule.onAllNodes(hasTestTag("liveness_check_screen")).fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodes(hasTestTag("verification_result")).fetchSemanticsNodes().isNotEmpty()
            }
            // If liveness appeared, simulate pass
            if (rule.onAllNodes(hasTestTag("liveness_check_screen")).fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithText("Simulate Pass", substring = true).performClick()
                rule.waitForIdle()
            }
            // Now wait for result screen
            rule.waitUntil(timeoutMillis = 10000) {
                rule.onAllNodes(hasTestTag("verification_result")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /** The scenario name for the current BDD scenario. */
    var pendingScenario: String = "happy"

    /** Whether demo mode is active. */
    var demoMode: Boolean = true

    /** Whether the vault should be empty. */
    var demoEmpty: Boolean = false

    /** Whether the activity has been launched for the current scenario. */
    var launched: Boolean = false

    val rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
        get() = sharedRule ?: throw IllegalStateException(
            "composeTestRule not initialized — CommonSteps must be instantiated first"
        )
}
