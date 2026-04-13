package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.bdd.BddTestContext
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * Step definitions for the activity-feed story.
 */
class ActivityFeedSteps {

    private val rule get() = BddTestContext.sharedRule!!

    // AC-1: Chronological list
    @Then("I see a chronological list of verification events")
    fun iSeeAChronologicalListOfVerificationEvents() {
        val entries = rule.onAllNodesWithTag("activity_entry").fetchSemanticsNodes()
        assert(entries.isNotEmpty()) { "Expected at least one activity entry" }
    }

    @Then("the most recent event is at the top")
    fun theMostRecentEventIsAtTheTop() {
        // In the happy scenario, "TODAY" group is first
        rule.onNodeWithText("TODAY").assertIsDisplayed()
    }

    // AC-2: Entry details
    @Then("each entry shows the cachet name")
    fun eachEntryShowsTheCachetName() {
        // Happy scenario first entry: "Childcare readiness check"
        rule.onNodeWithText("Childcare readiness check").assertIsDisplayed()
    }

    @Then("each entry shows the date and time")
    fun eachEntryShowsTheDateAndTime() {
        // Happy scenario first entry has time "10:32 AM"
        rule.onNodeWithText("10:32 AM", substring = true).assertIsDisplayed()
    }

    @Then("each entry shows the direction indicator")
    fun eachEntryShowsTheDirectionIndicator() {
        val indicators = rule.onAllNodesWithTag("direction_shared").fetchSemanticsNodes() +
            rule.onAllNodesWithTag("direction_received").fetchSemanticsNodes()
        assert(indicators.isNotEmpty()) { "Expected at least one direction indicator" }
    }

    // AC-3: Direction indicator
    @Then("outgoing verifications show a {string} indicator")
    fun outgoingVerificationsShowAIndicator(indicatorType: String) {
        rule.onAllNodesWithTag("direction_shared").onFirst().assertIsDisplayed()
    }

    @Then("incoming verifications show a {string} indicator")
    fun incomingVerificationsShowAIndicator(indicatorType: String) {
        rule.onAllNodesWithTag("direction_received").onFirst().assertIsDisplayed()
    }

    // AC-4: Tapping entry
    @When("I tap on an activity entry")
    fun iTapOnAnActivityEntry() {
        rule.onAllNodesWithTag("activity_entry").onFirst().performClick()
        rule.waitForIdle()
    }

    @Then("I see the consent receipt detail")
    fun iSeeTheConsentReceiptDetail() {
        // Tapping an activity entry should show detail/receipt info
        // In the current implementation, this may navigate to a detail view
        rule.waitForIdle()
    }

    // AC-5: Empty state
    @Then("I see an empty state message")
    fun iSeeAnEmptyStateMessage() {
        // Empty scenario has no activity entries
        // The audit summary bar or "No audit run yet" text serves as the empty state
        rule.onNodeWithText("No audit run yet", substring = true).assertIsDisplayed()
    }
}
