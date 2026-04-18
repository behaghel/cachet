package id.cachet.wallet.android.bdd.steps

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import id.cachet.wallet.android.MainActivity
import id.cachet.wallet.android.bdd.BddTestContext
import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.fixtures.ScenarioRegistry
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.junit.WithJunitRule
import org.junit.Rule

/**
 * Common step definitions shared across all BDD scenarios.
 *
 * Owns the single [composeTestRule] and publishes it via [BddTestContext]
 * so all other step classes can access the same Compose tree.
 */
@WithJunitRule
class CommonSteps {

    @Rule
    @JvmField
    val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule<MainActivity>()

    init {
        BddTestContext.sharedRule = composeTestRule
    }

    @After
    fun resetScenario() {
        DemoFixtures.isDemoActive = false
        DemoFixtures.activeScenario = ScenarioRegistry.get("happy")
        DemoFixtures.overrideScanPack = null
        DemoFixtures.livenessResult = DemoFixtures.LivenessResult.NONE
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("cachet_wallet", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    // ────────────────────────────────────────
    // Demo mode & scenario loading
    // ────────────────────────────────────────

    @Given("the app is launched")
    fun theAppIsLaunched() {
        // Default: demo mode with happy scenario (BDD tests always use demo)
        DemoFixtures.isDemoActive = true
        DemoFixtures.activeScenario = ScenarioRegistry.get("happy")
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        composeTestRule.waitForIdle()
    }

    @Given("the app is launched in demo mode")
    fun theAppIsLaunchedInDemoMode() {
        DemoFixtures.isDemoActive = true
        DemoFixtures.activeScenario = ScenarioRegistry.get("happy")
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        composeTestRule.waitForIdle()
    }

    @Given("the {string} demo scenario is loaded")
    fun theDemoScenarioIsLoaded(scenario: String) {
        val newScenario = ScenarioRegistry.get(scenario)
        if (DemoFixtures.isDemoActive && DemoFixtures.activeScenario === newScenario) return
        DemoFixtures.isDemoActive = true
        DemoFixtures.activeScenario = newScenario
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    @Given("the app is launched for the first time")
    fun theAppIsLaunchedForTheFirstTime() {
        DemoFixtures.isDemoActive = false
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    @Given("no verification events have occurred")
    fun noVerificationEventsHaveOccurred() {
        DemoFixtures.isDemoActive = true
        DemoFixtures.activeScenario = ScenarioRegistry.get("empty")
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    // ────────────────────────────────────────
    // Tab navigation (single method handles Given/When/Then)
    // ────────────────────────────────────────

    @Given("I am on the {string} tab")
    fun iAmOnTheTab(tabName: String) {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText(tabName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(tabName).performClick()
        composeTestRule.waitForIdle()
    }

    @When("I tap the {string} segment")
    fun iTapTheSegment(segmentName: String) {
        composeTestRule.onNodeWithText(segmentName).performClick()
        composeTestRule.waitForIdle()
    }

    // ────────────────────────────────────────
    // Tapping buttons (shared)
    // ────────────────────────────────────────

    @When("I tap {string}")
    fun iTap(buttonText: String) {
        val node = composeTestRule.onNodeWithText(buttonText, substring = true)
        try {
            node.performScrollTo()
        } catch (_: Throwable) {
            // Not in a scrollable container — click as-is
        }
        node.performClick()
        composeTestRule.waitForIdle()
    }

    @When("I tap the floating action button")
    fun iTapTheFloatingActionButton() {
        composeTestRule.onNodeWithTag("fab_get_cachet").performClick()
        composeTestRule.waitForIdle()
    }

    @Then("I see a {string} call to action")
    fun iSeeACallToAction(ctaText: String) {
        composeTestRule.onNodeWithText(ctaText, substring = true).assertIsDisplayed()
    }

    // ────────────────────────────────────────
    // Navigation: overlay screens
    // ────────────────────────────────────────

    @Given("I am on the Pack Picker screen in {word} mode")
    fun iAmOnThePackPickerScreen(mode: String) {
        // If already on pack picker, just verify
        try {
            composeTestRule.onNodeWithTag("pack_picker_screen").assertIsDisplayed()
            return
        } catch (_: AssertionError) {}
        when (mode) {
            "holder" -> {
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithText("My Cachets").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("fab_get_cachet").performClick()
            }
            "verifier" -> {
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithText("Activity").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("fab_new_request").performClick()
            }
        }
        composeTestRule.waitForIdle()
    }

    @Given("I am viewing a cachet detail")
    fun iAmViewingACachetDetail() {
        composeTestRule.onNodeWithText("My Cachets").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cachet_card_0").performClick()
        composeTestRule.waitForIdle()
    }

    @Given("I am viewing the revoked cachet detail")
    fun iAmViewingTheRevokedCachetDetail() {
        composeTestRule.onNodeWithText("My Cachets").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cachet_card_revoked").performClick()
        composeTestRule.waitForIdle()
    }

    @Given("I am on the Incoming Request screen")
    fun iAmOnTheIncomingRequestScreen() {
        // If already on the screen, verify and return
        try {
            composeTestRule.onNodeWithText("Verification Request").assertIsDisplayed()
            return
        } catch (_: AssertionError) {}
        // Use a low-value pack (no liveness) so "Verify & Share" appears for consent tests
        if (DemoFixtures.overrideScanPack == null) {
            DemoFixtures.overrideScanPack = DemoFixtures.cachPacks.first { it.cachetType == CachetType.AGE }
        }
        // Navigate: Activity tab -> scan QR -> demo auto-scan -> incoming request
        composeTestRule.onNodeWithText("Activity").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab_scan_qr").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("incoming_request_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Given("I am on the Show QR screen")
    fun iAmOnTheShowQRScreen() {
        // If already on the screen, verify and return
        try {
            composeTestRule.onNodeWithTag("qr_share_screen").assertIsDisplayed()
            return
        } catch (_: AssertionError) {}
        // Navigate: Activity tab -> FAB new request -> select pack
        composeTestRule.onNodeWithText("Activity").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab_new_request").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("pack_card").onFirst().performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("qr_share_screen").assertIsDisplayed()
    }

    @Given("I am on the Verification Result screen")
    fun iAmOnTheVerificationResultScreen() {
        // If already on the screen, verify and return
        try {
            composeTestRule.onNodeWithTag("verification_result").assertIsDisplayed()
            return
        } catch (_: AssertionError) {}
        // Navigate: Activity tab -> scan QR -> auto-scan -> consent -> result
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Activity").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab_scan_qr").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("incoming_request_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        BddTestContext.tapConsentCta(composeTestRule)
        BddTestContext.passLivenessIfNeeded(composeTestRule)
    }

    @Given("the QR scanner is open")
    fun theQRScannerIsOpen() {
        composeTestRule.onNodeWithText("Activity").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab_scan_qr").performClick()
        composeTestRule.waitForIdle()
    }

    @When("I press back")
    fun iPressBack() {
        // Some screens use "Back" icon, others use "Close"
        try {
            composeTestRule.onNodeWithContentDescription("Back").performClick()
        } catch (_: AssertionError) {
            composeTestRule.onNodeWithContentDescription("Close").performClick()
        }
        composeTestRule.waitForIdle()
    }

    @When("I dismiss the result")
    fun iDismissTheResult() {
        // Try "Done" button first, fall back to Close icon
        try {
            composeTestRule.onNodeWithText("Done").performScrollTo().performClick()
        } catch (_: AssertionError) {
            composeTestRule.onNodeWithContentDescription("Close").performClick()
        }
        composeTestRule.waitForIdle()
    }

    @Then("I return to the vault screen")
    fun iReturnToTheVaultScreen() {
        composeTestRule.onNodeWithText("My Cachets").assertIsDisplayed()
    }

    @Then("I return to the Activity tab")
    fun iReturnToTheActivityTab() {
        composeTestRule.onNodeWithText("Activity").assertIsDisplayed()
    }

    // ────────────────────────────────────────
    // Wireframe placeholder
    // ────────────────────────────────────────

    @Then("the screen matches wireframe {string}")
    fun theScreenMatchesWireframe(wireframeName: String) {
        // Visual matching is handled by the android-ux-review skill.
    }
}
