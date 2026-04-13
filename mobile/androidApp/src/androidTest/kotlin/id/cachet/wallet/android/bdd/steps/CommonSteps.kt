package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import id.cachet.wallet.android.MainActivity
import id.cachet.wallet.android.bdd.BddTestContext
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.fixtures.ScenarioRegistry
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Rule

/**
 * Common step definitions shared across all BDD scenarios.
 *
 * Owns the single [composeTestRule] and publishes it via [BddTestContext]
 * so all other step classes can access the same Compose tree.
 */
class CommonSteps {

    @get:Rule
    val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule<MainActivity>()

    init {
        BddTestContext.sharedRule = composeTestRule
    }

    @After
    fun resetScenario() {
        DemoFixtures.activeScenario = ScenarioRegistry.get("happy")
    }

    // ────────────────────────────────────────
    // Demo mode & scenario loading
    // ────────────────────────────────────────

    @Given("the app is launched in demo mode")
    fun theAppIsLaunchedInDemoMode() {
        composeTestRule.waitForIdle()
    }

    @Given("the {string} demo scenario is loaded")
    fun theDemoScenarioIsLoaded(scenario: String) {
        if (scenario == "happy") return
        DemoFixtures.activeScenario = ScenarioRegistry.get(scenario)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    @Given("the app is launched for the first time")
    fun theAppIsLaunchedForTheFirstTime() {
        composeTestRule.waitForIdle()
    }

    @Given("no verification events have occurred")
    fun noVerificationEventsHaveOccurred() {
        DemoFixtures.activeScenario = ScenarioRegistry.get("empty")
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    // ────────────────────────────────────────
    // Tab navigation (single method handles Given/When/Then)
    // ────────────────────────────────────────

    @Given("I am on the {string} tab")
    fun iAmOnTheTab(tabName: String) {
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
        composeTestRule.onNodeWithText(buttonText, substring = true).performClick()
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
        when (mode) {
            "holder" -> {
                composeTestRule.onNodeWithText("My Cachets").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("fab_get_cachet").performClick()
            }
            "verifier" -> {
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
        composeTestRule.onNodeWithText("Verification Request").assertIsDisplayed()
    }

    @Given("I am on the Show QR screen")
    fun iAmOnTheShowQRScreen() {
        composeTestRule.onNodeWithTag("qr_share_screen").assertIsDisplayed()
    }

    @Given("I am on the Verification Result screen")
    fun iAmOnTheVerificationResultScreen() {
        composeTestRule.onNodeWithTag("verification_result").assertIsDisplayed()
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
        composeTestRule.onNodeWithText("Back", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
    }

    @When("I dismiss the result")
    fun iDismissTheResult() {
        composeTestRule.onNodeWithText("Done").performClick()
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
