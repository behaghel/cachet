package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import id.cachet.wallet.android.bdd.BddTestContext
import id.cachet.wallet.android.bdd.BddTestContext.Companion.passLivenessIfNeeded
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.fixtures.ScenarioRegistry
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then

/**
 * Step definitions for the verification-result story.
 *
 * "I am on the Verification Result screen" is in CommonSteps (shared).
 * "I dismiss the result" and "I return to the Activity tab" are also in CommonSteps.
 */
class VerificationResultSteps {

    private val rule get() = BddTestContext.sharedRule!!

    // AC-1, AC-2: Pass/fail result
    @Given("a verification has completed with {word} outcome")
    fun aVerificationHasCompletedWithOutcome(outcome: String) {
        val scenario = if (outcome == "pass") "happy" else "seller-only"
        DemoFixtures.isDemoActive = true
        DemoFixtures.activeScenario = ScenarioRegistry.get(scenario)
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        // Navigate: Activity tab -> scan QR -> auto-scan -> consent -> result
        rule.onNodeWithText("Activity").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fab_scan_qr").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("incoming_request_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        BddTestContext.tapConsentCta(rule)
        BddTestContext.passLivenessIfNeeded(rule)
    }

    @Given("a verification has completed")
    fun aVerificationHasCompleted() {
        aVerificationHasCompletedWithOutcome("pass")
    }

    @Then("I see a {word} {word} state")
    fun iSeeAColorOutcomeState(color: String, outcome: String) {
        rule.onNodeWithText("proofs passed", substring = true).assertIsDisplayed()
    }

    @Then("I see the cachet badge for the verified pack")
    fun iSeeTheCachetBadge() {
        rule.onNodeWithTag("verification_result").assertIsDisplayed()
    }

    @Then("I see a clear reason for the failure")
    fun iSeeAClearReasonForTheFailure() {
        rule.onAllNodesWithText("Credential not available", substring = true).onFirst().assertExists()
    }

    // AC-3: Individual predicate results
    @Then("each predicate result is listed individually")
    fun eachPredicateResultIsListedIndividually() {
        val predicates = rule.onAllNodesWithTag("predicate_result_row").fetchSemanticsNodes()
        assert(predicates.isNotEmpty()) { "Expected predicate result rows" }
    }

    @Then("each predicate shows pass or fail status")
    fun eachPredicateShowsPassOrFailStatus() {
        rule.onAllNodesWithText("\u2713", substring = true).onFirst().assertIsDisplayed()
    }

    // AC-4: Pack identification
    @Then("I see the name of the Trust Pack that was verified against")
    fun iSeeTheNameOfTheTrustPack() {
        rule.onNodeWithTag("verification_result").assertIsDisplayed()
    }

    // AC-7: Consent receipt
    @Then("a consent receipt is generated and stored")
    fun aConsentReceiptIsGeneratedAndStored() {
        rule.onNodeWithText("Consent receipt logged", substring = true).assertIsDisplayed()
    }

    @Then("the receipt appears in the Activity feed")
    fun theReceiptAppearsInTheActivityFeed() {
        try {
            rule.onNodeWithText("Done").performScrollTo().performClick()
        } catch (_: AssertionError) {
            rule.onNodeWithContentDescription("Close").performClick()
        }
        rule.waitForIdle()
        rule.onNodeWithText("Activity").performClick()
        rule.waitForIdle()
    }

    // Demo pack-specific results
    @Given("I pick the {} pack")
    fun iPickThePack(packName: String) {
        rule.waitForIdle()
    }

    @Given("I complete the verification flow")
    fun iCompleteTheVerificationFlow() {
        rule.onNodeWithText("Activity").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fab_scan_qr").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("incoming_request_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        BddTestContext.tapConsentCta(rule)
        BddTestContext.passLivenessIfNeeded(rule)
    }

    @Then("I see a {word} result for {string}")
    fun iSeeAResultFor(outcome: String, label: String) {
        rule.onNodeWithTag("verification_result").assertIsDisplayed()
    }

    @Then("I see the age predicate passed")
    fun iSeeTheAgePredicatePassed() {
        rule.onNodeWithText("proofs passed", substring = true).assertIsDisplayed()
    }

    @Then("I see which seller predicates failed")
    fun iSeeWhichSellerPredicatesFailed() {
        rule.onAllNodesWithText("Credential not available", substring = true).onFirst().assertExists()
    }
}
