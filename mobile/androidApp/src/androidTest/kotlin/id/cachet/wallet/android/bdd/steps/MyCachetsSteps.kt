package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.bdd.BddTestContext
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * Step definitions for the my-cachets story.
 *
 * Shared steps like "I tap the floating action button" and "I see a {string}
 * call to action" are in CommonSteps.
 */
class MyCachetsSteps {

    private val rule get() = BddTestContext.sharedRule!!

    @Then("I see cachet cards for each stored credential")
    fun iSeeCachetCardsForEachStoredCredential() {
        val nodes = rule.onAllNodesWithTag("cachet_card", useUnmergedTree = true).fetchSemanticsNodes()
        assert(nodes.isNotEmpty()) { "Expected at least one cachet card" }
    }

    @Then("each card shows the cachet name, badge icon, and trust status")
    fun eachCardShowsDetails() {
        rule.onAllNodesWithTag("trust_status_chip").onFirst().assertIsDisplayed()
    }

    @When("I tap on a cachet card")
    fun iTapOnACachetCard() {
        rule.onNodeWithTag("cachet_card_0").performClick()
        rule.waitForIdle()
    }

    @Then("I am navigated to the Cachet Detail screen")
    fun iAmNavigatedToCachetDetailScreen() {
        rule.onNodeWithTag("cachet_detail_screen").assertIsDisplayed()
    }

    @Then("I see an empty state illustration")
    fun iSeeAnEmptyStateIllustration() {
        rule.onNodeWithText("Your vault is empty").assertIsDisplayed()
    }

    @Then("I am navigated to the Pack Picker in holder mode")
    fun iAmNavigatedToThePackPickerInHolderMode() {
        rule.onNodeWithText("Get a new cachet").assertIsDisplayed()
    }

    @Given("the {string} demo scenario is loaded and I am on the empty vault screen")
    fun theDemoScenarioIsLoadedAndIAmOnTheEmptyVaultScreen(scenario: String) {
        rule.onNodeWithText("Your vault is empty").assertIsDisplayed()
    }
}
