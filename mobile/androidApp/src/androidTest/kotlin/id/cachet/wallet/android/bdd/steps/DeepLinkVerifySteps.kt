package id.cachet.wallet.android.bdd.steps

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.bdd.BddTestContext
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.fixtures.ScenarioRegistry
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * Step definitions for the deep-link-verify story.
 */
class DeepLinkVerifySteps {

    private val rule get() = BddTestContext.sharedRule!!

    @Given("I have a valid identity cachet")
    fun iHaveAValidIdentityCachet() {
        DemoFixtures.isDemoActive = true
        DemoFixtures.activeScenario = ScenarioRegistry.get("happy")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Given("the app is in the foreground on the {string} tab")
    fun theAppIsInTheForegroundOnTheTab(tabName: String) {
        DemoFixtures.isDemoActive = true
        DemoFixtures.activeScenario = ScenarioRegistry.get("happy")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithText(tabName).assertIsDisplayed()
    }

    @Given("I arrived via a deep link and I am on the Incoming Request screen")
    fun iArrivedViaADeepLinkAndIAmOnTheIncomingRequestScreen() {
        DemoFixtures.isDemoActive = true
        DemoFixtures.activeScenario = ScenarioRegistry.get("happy")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        deliverDeepLink("cachet://verify?pack=childcare")
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodesWithText("Verification Request").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Given("I have no credentials in my vault")
    fun iHaveNoCredentialsInMyVault() {
        DemoFixtures.isDemoActive = true
        DemoFixtures.activeScenario = ScenarioRegistry.get("empty")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @When("I open the deep link {string}")
    fun iOpenTheDeepLink(uri: String) {
        deliverDeepLink(uri)
        rule.waitForIdle()
        Thread.sleep(1000)
        rule.waitForIdle()
    }

    // "I see the Incoming Request screen" reused from ScanToVerifySteps

    @Then("the requested Trust Pack is {string}")
    fun theRequestedTrustPackIs(packName: String) {
        // The pack question or name should be visible on the Incoming Request screen
        rule.waitForIdle()
        // Pack names map to questions in the request screen
        val expectedText = when (packName) {
            "Childcare Ready" -> "Are you safe for childcare?"
            "Trusted Seller" -> "Are you a trusted seller?"
            else -> packName
        }
        rule.onNodeWithText(expectedText, substring = true).assertIsDisplayed()
    }

    @Then("I see an error message indicating the session is unavailable")
    fun iSeeAnErrorMessageIndicatingTheSessionIsUnavailable() {
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodesWithText("Request Expired").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Request Expired").assertIsDisplayed()
    }

    @Then("I am returned to the vault screen")
    fun iAmReturnedToTheVaultScreen() {
        // On the expired screen, tap "Back to Vault" to return
        try {
            rule.onNodeWithText("Back to Vault").performClick()
            rule.waitForIdle()
        } catch (_: AssertionError) {
            // Already on vault
        }
        rule.onNodeWithText("My Cachets").assertIsDisplayed()
    }

    @Then("I return to the vault screen and no credentials are shared")
    fun iReturnToTheVaultScreenAndNoCredentialsAreShared() {
        rule.waitForIdle()
        rule.onNodeWithText("My Cachets").assertIsDisplayed()
    }

    @Then("I see a message that identity verification is required first")
    fun iSeeAMessageThatIdentityVerificationIsRequiredFirst() {
        rule.waitForIdle()
        rule.onNodeWithText("Verify your identity", substring = true).assertIsDisplayed()
    }

    @Then("I am offered to start the identity verification flow")
    fun iAmOfferedToStartTheIdentityVerificationFlow() {
        rule.onNodeWithText("Get your first cachet", substring = true).assertIsDisplayed()
    }

    private fun deliverDeepLink(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        rule.activityRule.scenario.onActivity { activity ->
            activity.onNewIntent(intent)
        }
    }
}
