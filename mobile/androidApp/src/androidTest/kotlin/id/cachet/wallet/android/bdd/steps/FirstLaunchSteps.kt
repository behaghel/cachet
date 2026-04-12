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
 * Step definitions for the first-launch (onboarding) story.
 */
class FirstLaunchSteps {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Then("I see the first onboarding screen")
    fun iSeeTheFirstOnboardingScreen() {
        composeTestRule.onNodeWithText("Don't take their word for it").assertExists()
    }

    @Then("the headline is {string}")
    fun theHeadlineIs(headline: String) {
        composeTestRule.onNodeWithText(headline).assertExists()
    }

    @Given("I am on onboarding screen {int}")
    fun iAmOnOnboardingScreen(screenNumber: Int) {
        // Navigate to the specified onboarding screen by tapping Next
        repeat(screenNumber - 1) {
            composeTestRule.onNodeWithText("Next").performClick()
            composeTestRule.waitForIdle()
        }
    }

    @When("I tap {string}")
    fun iTap(buttonText: String) {
        composeTestRule.onNodeWithText(buttonText).performClick()
    }

    @Then("I am on onboarding screen {int}")
    fun iAmOnOnboardingScreenAssertion(screenNumber: Int) {
        // Each screen has a distinct value proposition — verify by content
        // TODO: add semantic test tags per onboarding screen for reliable matching
    }

    @Then("the screen conveys {string}")
    fun theScreenConveys(message: String) {
        // Verify the screen communicates the expected value proposition
        // This is a content assertion — look for key phrases
        composeTestRule.onNodeWithText(message, substring = true).assertExists()
    }

    @Then("I see a {string} call to action")
    fun iSeeACta(ctaText: String) {
        composeTestRule.onNodeWithText(ctaText).assertExists()
    }

    @Then("I am on the empty vault screen")
    fun iAmOnTheEmptyVaultScreen() {
        composeTestRule.onNodeWithText("Get your first cachet", substring = true).assertExists()
    }

    @Given("I have completed onboarding previously")
    fun iHaveCompletedOnboardingPreviously() {
        // TODO: set shared preference flag indicating onboarding complete
    }

    @When("I launch the app")
    fun iLaunchTheApp() {
        // TODO: re-launch activity
    }

    @Then("I am taken directly to the vault screen")
    fun iAmTakenDirectlyToTheVaultScreen() {
        composeTestRule.onNodeWithText("My Cachets").assertExists()
    }

    @Then("the step indicator shows {string}")
    fun theStepIndicatorShows(progress: String) {
        composeTestRule.onNodeWithText(progress, substring = true).assertExists()
    }
}
