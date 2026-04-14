package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.bdd.BddTestContext
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * Step definitions for the get-new-cachet story.
 *
 * Shared steps (tab navigation, FAB, back, "I tap {string}") are in CommonSteps.
 */
class GetNewCachetSteps {

    private val rule get() = BddTestContext.sharedRule!!

    // AC-1: Pack list
    @Then("I see all available Trust Packs from the registry")
    fun iSeeAllAvailableTrustPacks() {
        val packs = rule.onAllNodesWithTag("pack_card").fetchSemanticsNodes()
        assert(packs.isNotEmpty()) { "Expected at least one pack card" }
    }

    // AC-2: Pack card details
    @Then("each pack card shows the pack name")
    fun eachPackCardShowsThePackName() {
        rule.onNodeWithText("Safe for my kids?").assertIsDisplayed()
    }

    @Then("each pack card shows a description")
    fun eachPackCardShowsADescription() {
        rule.onNodeWithText("Identity, background check, references").assertIsDisplayed()
    }

    @Then("each pack card shows the required verification type")
    fun eachPackCardShowsTheVerificationType() {
        rule.onAllNodesWithText("proofs required", substring = true).onFirst().assertIsDisplayed()
    }

    // AC-3: Selecting a pack
    @When("I tap on a Trust Pack")
    fun iTapOnATrustPack() {
        rule.onAllNodesWithTag("pack_card").onFirst().performClick()
        rule.waitForIdle()
    }

    @Then("the credential acquisition flow begins")
    fun theCredentialAcquisitionFlowBegins() {
        rule.onNodeWithText("Verification Request").assertIsDisplayed()
    }

    // AC-1: Empty vault → identity verification (not pack picker)
    @Then("the identity verification flow begins")
    fun theIdentityVerificationFlowBegins() {
        // In demo mode, identity verification is simulated instantly —
        // the vault transitions from empty to showing the identity cachet.
        rule.waitForIdle()
        rule.onNodeWithText("Identity").assertIsDisplayed()
    }

    @Then("I do not see the Pack Picker screen")
    fun iDoNotSeeThePackPickerScreen() {
        rule.onAllNodesWithText("Pick a Trust Pack", substring = true)
            .fetchSemanticsNodes().let { nodes ->
                assert(nodes.isEmpty()) { "Pack Picker should not be visible from empty vault" }
            }
    }
}
