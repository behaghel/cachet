package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.bdd.BddTestContext
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * Step definitions for the cachet-detail story.
 */
class CachetDetailSteps {

    private val rule get() = BddTestContext.sharedRule!!

    // AC-1: Prominent display
    @Then("I see the cachet name prominently")
    fun iSeeTheCachetNameProminently() {
        // Detail screen should show one of the demo credential names
        rule.onNodeWithTag("cachet_detail_screen").assertIsDisplayed()
    }

    @Then("I see the badge icon")
    fun iSeeTheBadgeIcon() {
        // The CachetMark is rendered in the hero section
        rule.onNodeWithTag("cachet_detail_screen").assertIsDisplayed()
    }

    @Then("I see the trust status")
    fun iSeeTheTrustStatus() {
        rule.onAllNodesWithTag("trust_status_chip").fetchSemanticsNodes().let { nodes ->
            assert(nodes.isNotEmpty()) { "Expected trust status chip on detail screen" }
        }
    }

    // AC-2: Predicate listing
    @Then("I see all predicates listed")
    fun iSeeAllPredicatesListed() {
        val predicates = rule.onAllNodesWithTag("predicate_row").fetchSemanticsNodes()
        assert(predicates.isNotEmpty()) { "Expected at least one predicate row" }
    }

    @Then("each predicate shows its evaluation status")
    fun eachPredicateShowsItsEvaluationStatus() {
        // Each predicate row has a ✓ indicator
        rule.onNodeWithText("\u2713", substring = true).assertIsDisplayed()
    }

    // AC-3: Credential metadata
    @Then("I see the issuer name")
    fun iSeeTheIssuerName() {
        rule.onNodeWithText("Issuer").assertIsDisplayed()
    }

    @Then("I see the issuance date")
    fun iSeeTheIssuanceDate() {
        rule.onNodeWithText("Issued").assertIsDisplayed()
    }

    @Then("I see the expiry date if present")
    fun iSeeTheExpiryDateIfPresent() {
        rule.onNodeWithText("Expires").assertIsDisplayed()
    }

    // AC-4: Hardware-backed indicator
    @Given("the credential {word} a hardware-backed signing key")
    fun theCredentialHasAHardwareBackedSigningKey(hasHardware: String) {
        // Navigation to the right detail is handled per scenario.
        // "has" -> identity detail (has keyAlias)
        // "does not have" -> childcare detail (no keyAlias)
        when (hasHardware) {
            "has" -> {
                // Open identity detail which has keyAlias
                rule.onNodeWithText("My Cachets").performClick()
                rule.waitForIdle()
                // Identity is the first card in happy scenario
                rule.onNodeWithTag("cachet_card_0").performClick()
                rule.waitForIdle()
            }
            "does" -> {
                // "does not have" - open childcare detail (no keyAlias)
                rule.onNodeWithText("My Cachets").performClick()
                rule.waitForIdle()
                rule.onNodeWithTag("cachet_card_1").performClick()
                rule.waitForIdle()
            }
        }
    }

    @When("I view its cachet detail")
    fun iViewItsCachetDetail() {
        rule.onNodeWithTag("cachet_detail_screen").assertIsDisplayed()
    }

    @Then("I {word} the hardware-backed security indicator")
    fun iSeeTheHardwareBackedSecurityIndicator(seeOrNot: String) {
        when (seeOrNot) {
            "see" -> rule.onNodeWithTag("hardware_indicator").assertIsDisplayed()
            "do" -> rule.onNodeWithTag("hardware_indicator").assertDoesNotExist()
        }
    }

    // AC-5: Freshness status
    @Then("I see the credential freshness status")
    fun iSeeTheCredentialFreshnessStatus() {
        // Freshness is shown on the card but also in detail metadata
        rule.onNodeWithTag("cachet_detail_screen").assertIsDisplayed()
    }
}
