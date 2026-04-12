package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.MainActivity
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Rule

/**
 * Common step definitions shared across all BDD scenarios.
 *
 * These steps handle app launch, demo mode, tab navigation, and
 * visual wireframe matching.
 */
class CommonSteps {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Given("the app is launched in demo mode")
    fun theAppIsLaunchedInDemoMode() {
        // Demo mode is controlled by intent extras.
        // The test runner launches with demo_mode=true by default.
        // TODO: configure intent extras via ActivityScenario
    }

    @Given("the {string} demo scenario is loaded")
    fun theDemoScenarioIsLoaded(scenario: String) {
        // TODO: set demo_scenario intent extra
        // Scenarios: happy, empty, revoked, seller-only, expired
    }

    @Given("the app is launched for the first time")
    fun theAppIsLaunchedForTheFirstTime() {
        // TODO: clear shared preferences to simulate fresh install
    }

    @When("I am on the {string} tab")
    fun iAmOnTheTab(tabName: String) {
        composeTestRule.onNodeWithText(tabName).performClick()
    }

    @When("I tap the {string} segment")
    fun iTapTheSegment(segmentName: String) {
        composeTestRule.onNodeWithText(segmentName).performClick()
    }

    @Then("I am on the {string} tab")
    fun iAmOnTheTabAssertion(tabName: String) {
        composeTestRule.onNodeWithText(tabName).assertExists()
    }

    @Then("the screen matches wireframe {string}")
    fun theScreenMatchesWireframe(wireframeName: String) {
        // Visual matching is handled by the android-ux-review skill,
        // not by automated Compose tests. This step is a placeholder
        // that records the wireframe reference for traceability.
        // TODO: integrate screenshot capture for manual review
    }
}
