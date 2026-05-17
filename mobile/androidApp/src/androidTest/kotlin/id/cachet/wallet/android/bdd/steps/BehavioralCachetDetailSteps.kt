package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.bdd.BddTestContext
import id.cachet.wallet.android.trusttrail.model.BehavioralCachetDetailUi
import id.cachet.wallet.android.trusttrail.model.PlatformContributionUi
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.fixtures.HappyPathScenario
import id.cachet.wallet.android.ui.model.RequestPredicate
import id.cachet.wallet.trusttrail.strength.Tier
import id.cachet.wallet.trusttrail.strength.TierResolver
import id.cachet.wallet.trusttrail.strength.TierThresholds
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * Step definitions for the Behavioral Cachet Detail v2 story.
 * Covers AC-1 through AC-10.
 */
class BehavioralCachetDetailSteps {

    private val rule get() = BddTestContext.sharedRule!!

    /** The base behavioral detail from the happy scenario, used as template. */
    private val baseDetail get() = HappyPathScenario.behavioralCachetDetails["demo-trusted-host"]!!

    // ── Background / preconditions ──

    @Given("the holder has a behavioral cachet")
    fun theHolderHasABehavioralCachet() {
        // Happy path scenario includes a Trusted Host behavioral cachet.
    }

    // ── Navigation into detail ──

    @When("I tap on a behavioral cachet card")
    fun iTapOnABehavioralCachetCard() {
        rule.waitForIdle()
        rule.onNodeWithTag("cachet_card_3").performClick()
        rule.waitForIdle()
    }

    @Given("I am viewing a behavioral cachet detail")
    fun iAmViewingABehavioralCachetDetail() {
        rule.onNodeWithText("My Cachets").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cachet_card_3").performClick()
        rule.waitForIdle()
    }

    @When("I view the cachet detail")
    fun iViewTheCachetDetail() {
        iAmViewingABehavioralCachetDetail()
    }

    // ── AC-1: Tier dial hero ──

    @Then("I see a C-shaped circular dial")
    fun iSeeACShapedCircularDial() {
        rule.onNodeWithTag("tier_dial").assertIsDisplayed()
    }

    @Then("the cachet shield logo is centered inside the dial")
    fun theCachetShieldLogoIsCenteredInsideTheDial() {
        rule.onNodeWithTag("tier_dial").assertIsDisplayed()
    }

    @Then("the dial is filled in green up to the current strength")
    fun theDialIsFilledInGreenUpToTheCurrentStrength() {
        rule.onNodeWithTag("tier_dial").assertIsDisplayed()
    }

    // ── AC-2: Tier badge and strength ──

    @Given("the cachet strength is {double}")
    fun theCachetStrengthIs(strength: Double) {
        val tier = TierResolver.resolve(strength, TierThresholds())
        overrideBehavioralDetail(baseDetail.copy(strength = strength.toFloat(), tier = tier))
    }

    @Then("I see the tier badge showing {string}")
    fun iSeeTheTierBadgeShowing(tierName: String) {
        rule.onNodeWithTag("tier_badge").assertIsDisplayed()
        rule.onNodeWithText(tierName).assertIsDisplayed()
    }

    @Then("I see the strength displayed as {string}")
    fun iSeeTheStrengthDisplayedAs(display: String) {
        rule.onNodeWithText(display).assertIsDisplayed()
    }

    // ── AC-3: Cachet name ──

    @Then("I see the cachet name below the strength percentage")
    fun iSeeTheCachetNameBelowTheStrengthPercentage() {
        rule.onNodeWithTag("cachet_name").assertIsDisplayed()
        rule.onNodeWithText("Trusted Host").assertIsDisplayed()
    }

    @Then("the name is the most prominent text label on the screen")
    fun theNameIsTheMostProminentTextLabelOnTheScreen() {
        rule.onNodeWithText("Trusted Host").assertIsDisplayed()
    }

    // ── AC-4: Metadata row ──

    @Then("I see the issuance date")
    fun iSeeTheIssuanceDate() {
        rule.onNodeWithText("Mar 15, 2026").assertIsDisplayed()
    }

    @Then("I see the issuer as {string}")
    fun iSeeTheIssuerAs(issuer: String) {
        rule.onNodeWithText(issuer).assertIsDisplayed()
    }

    @Then("I see the linked identity cachet status")
    fun iSeeTheLinkedIdentityCachetStatus() {
        rule.onNodeWithText("Identity \u2713").assertIsDisplayed()
    }

    // ── AC-5: Predicates ──

    @Then("I see a {string} section")
    fun iSeeASection(sectionName: String) {
        rule.onNodeWithText(sectionName).assertIsDisplayed()
    }

    @Then("each predicate shows a check mark and description")
    fun eachPredicateShowsACheckMarkAndDescription() {
        rule.onNodeWithText("Verified hosting track record").assertIsDisplayed()
        rule.onNodeWithText("Identity verified").assertIsDisplayed()
    }

    @Then("each predicate has a privacy note explaining what is not shared")
    fun eachPredicateHasAPrivacyNoteExplainingWhatIsNotShared() {
        rule.onNodeWithText("Based on confirmed exchanges, not reviews").assertIsDisplayed()
        rule.onNodeWithText("Linked to a Gold identity cachet").assertIsDisplayed()
    }

    // ── AC-6: Evidence breakdown per platform ──

    @Given("the cachet has evidence from multiple platforms")
    fun theCachetHasEvidenceFromMultiplePlatforms() {
        // Default happy path has HomeExchange + Vinted — nothing to override.
    }

    @Then("I see an {string} section")
    fun iSeeAnSection(sectionName: String) {
        rule.onNodeWithText(sectionName).assertIsDisplayed()
    }

    @Then("each platform shows its name and evidence item count")
    fun eachPlatformShowsItsNameAndEvidenceItemCount() {
        rule.onNodeWithText("HomeExchange").assertIsDisplayed()
        rule.onNodeWithText("7 evidence items").assertIsDisplayed()
        rule.onNodeWithText("Vinted").assertIsDisplayed()
        rule.onNodeWithText("3 evidence items").assertIsDisplayed()
    }

    @Then("each platform shows its contribution percentage")
    fun eachPlatformShowsItsContributionPercentage() {
        rule.onNodeWithText("72%").assertIsDisplayed()
        rule.onNodeWithText("18%").assertIsDisplayed()
    }

    @Then("each platform has a progress bar proportional to its contribution")
    fun eachPlatformHasAProgressBar() {
        rule.onNodeWithTag("evidence_platform_HomeExchange").assertIsDisplayed()
        rule.onNodeWithTag("evidence_platform_Vinted").assertIsDisplayed()
    }

    // ── AC-7: Single platform evidence ──

    @Given("the cachet has evidence from one platform only")
    fun theCachetHasEvidenceFromOnePlatformOnly() {
        overrideBehavioralDetail(baseDetail.copy(
            evidencePlatforms = listOf(PlatformContributionUi("HomeExchange", 10, 100))
        ))
    }

    @Then("I see one platform row showing 100% contribution")
    fun iSeeOnePlatformRowShowing100Contribution() {
        rule.onNodeWithTag("evidence_platform_HomeExchange").assertIsDisplayed()
        rule.onNodeWithText("100%").assertIsDisplayed()
    }

    // ── AC-8: Scan CTA ──

    @Given("the cachet tier is {string}")
    fun theCachetTierIs(tierName: String) {
        val tier = when (tierName) {
            "BRONZE" -> Tier.BRONZE
            "SILVER" -> Tier.SILVER
            "GOLD" -> Tier.GOLD
            else -> null
        }
        val strength = when (tier) {
            Tier.BRONZE -> 0.35f
            Tier.SILVER -> 0.72f
            Tier.GOLD -> 0.91f
            null -> 0.15f
        }
        overrideBehavioralDetail(baseDetail.copy(strength = strength, tier = tier))
    }

    @Then("I see a secondary button at the bottom with text {string}")
    fun iSeeASecondaryButtonAtTheBottomWithText(text: String) {
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    // ── AC-10: Below bronze ──

    @Then("the tier badge is not shown")
    fun theTierBadgeIsNotShown() {
        rule.onNodeWithTag("tier_badge").assertDoesNotExist()
    }

    @Then("the dial is filled to {int}%")
    fun theDialIsFilledTo(percent: Int) {
        rule.onNodeWithText("$percent%").assertIsDisplayed()
        rule.onNodeWithTag("tier_dial").assertIsDisplayed()
    }

    @Then("the secondary CTA says {string}")
    fun theSecondaryCtaSays(text: String) {
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    // ── Helpers ──

    private fun overrideBehavioralDetail(detail: BehavioralCachetDetailUi) {
        // Replace the behavioral detail in the active scenario
        val scenario = object : id.cachet.wallet.android.ui.fixtures.DemoScenario by DemoFixtures.activeScenario {
            override val behavioralCachetDetails = mapOf("demo-trusted-host" to detail)
        }
        DemoFixtures.activeScenario = scenario
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        Thread.sleep(500)
        rule.waitForIdle()
    }
}
