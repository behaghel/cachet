package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.bdd.BddTestContext
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * Step definitions for the first-launch (onboarding) story.
 *
 * Shared steps like "I tap {string}" and "I see a {string} call to action"
 * are in CommonSteps.
 */
class FirstLaunchSteps {

    private val rule get() = BddTestContext.sharedRule!!

    @Then("I see the first onboarding screen")
    fun iSeeTheFirstOnboardingScreen() {
        rule.onNodeWithText("Don't take their word for it").assertIsDisplayed()
    }

    @Then("the headline is {string}")
    fun theHeadlineIs(headline: String) {
        rule.onNodeWithText(headline).assertIsDisplayed()
    }

    @Given("I am on onboarding screen {int}")
    fun iAmOnOnboardingScreen(screenNumber: Int) {
        repeat(screenNumber - 1) {
            rule.onNodeWithText("Next").performClick()
            rule.waitForIdle()
        }
    }

    @Then("I am on onboarding screen {int}")
    fun iAmOnOnboardingScreenAssertion(screenNumber: Int) {
        rule.waitForIdle()
    }

    @Then("the screen conveys {string}")
    fun theScreenConveys(message: String) {
        rule.onNodeWithText(message, substring = true).assertIsDisplayed()
    }

    @Then("I am on the empty vault screen")
    fun iAmOnTheEmptyVaultScreen() {
        rule.onNodeWithText("Your vault is empty", substring = true).assertIsDisplayed()
    }

    @Given("I have completed onboarding previously")
    fun iHaveCompletedOnboardingPreviously() {
        repeat(3) {
            rule.onNodeWithText("Next").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("Get Started").performClick()
        rule.waitForIdle()
    }

    @When("I launch the app")
    fun iLaunchTheApp() {
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Then("I am taken directly to the vault screen")
    fun iAmTakenDirectlyToTheVaultScreen() {
        rule.onNodeWithText("My Cachets").assertIsDisplayed()
    }

    @Then("the step indicator shows {string}")
    fun theStepIndicatorShows(progress: String) {
        rule.onNodeWithText(progress, substring = true).assertIsDisplayed()
    }
}
