package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.MainActivity
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Rule

/**
 * Step definitions for the my-cachets story.
 */
class MyCachetsSteps {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Then("I see cachet cards for each stored credential")
    fun iSeeCachetCardsForEachStoredCredential() {
        // TODO: assert on semantic test tags for cachet cards
        // In demo happy scenario, expect at least one card
        composeTestRule.onAllNodesWithTag("cachet_card").fetchSemanticsNodes().isNotEmpty()
    }

    @Then("each card shows the cachet name, badge icon, and trust status")
    fun eachCardShowsDetails() {
        // TODO: verify card content — needs semantic test tags on card components
    }

    @When("I tap on a cachet card")
    fun iTapOnACachetCard() {
        composeTestRule.onAllNodesWithTag("cachet_card").onFirst().performClick()
    }

    @Then("I am navigated to the Cachet Detail screen")
    fun iAmNavigatedToCachetDetailScreen() {
        // TODO: assert detail screen is shown — needs semantic test tag
    }

    @Then("I see an empty state illustration")
    fun iSeeAnEmptyStateIllustration() {
        composeTestRule.onNodeWithContentDescription("Empty vault").assertExists()
    }

    @Then("I see a {string} call to action")
    fun iSeeCallToAction(ctaText: String) {
        composeTestRule.onNodeWithText(ctaText, substring = true).assertExists()
    }

    @When("I tap the floating action button")
    fun iTapTheFloatingActionButton() {
        composeTestRule.onNodeWithContentDescription("Add").performClick()
    }

    @Then("I am navigated to the Pack Picker in holder mode")
    fun iAmNavigatedToThePackPickerInHolderMode() {
        // TODO: assert pack picker screen in holder mode
    }
}
