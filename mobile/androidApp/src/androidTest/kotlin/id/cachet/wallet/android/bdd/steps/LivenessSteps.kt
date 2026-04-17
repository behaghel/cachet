package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import id.cachet.wallet.android.bdd.BddTestContext
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.mapper.CachPackMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * Step definitions for the liveness-before-signing story.
 *
 * Covers: per-pack consent routing, liveness gate, liveness pass/fail,
 * and cancel flows.
 */
class LivenessSteps {

    private val rule get() = BddTestContext.sharedRule!!

    // ────────────────────────────────────────
    // Given: Navigate to Incoming Request for a specific pack
    // ────────────────────────────────────────

    @Given("I am on the Incoming Request screen for a/an {string} pack")
    fun iAmOnTheIncomingRequestScreenForPack(packName: String) {
        val pack = DemoFixtures.cachPacks.firstOrNull { packDisplayName(it) == packName }
            ?: error("Unknown pack: $packName. Available: ${DemoFixtures.cachPacks.map { packDisplayName(it) }}")

        // Set this pack as the demo scan target
        DemoFixtures.overrideScanPack = pack

        // Navigate: Activity tab -> scan QR -> demo auto-scan -> incoming request
        rule.onNodeWithText("Activity").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fab_scan_qr").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodesWithTag("incoming_request_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify we see the right pack's question
        val expectedQuestion = CachPackMapper.toVerificationRequest(pack).question
        rule.onNodeWithText(expectedQuestion).assertIsDisplayed()
    }

    @Then("I see a face scan notice")
    fun iSeeAFaceScanNotice() {
        rule.onNodeWithTag("biometric_notice").assertIsDisplayed()
        rule.onNodeWithText("face scan", substring = true).assertIsDisplayed()
    }

    @Then("the action button reads {string}")
    fun theActionButtonReads(expectedText: String) {
        rule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Given("I hold a valid credential")
    fun iHoldAValidCredential() {
        // In demo mode with happy scenario, we already have credentials — no-op
    }

    // ────────────────────────────────────────
    // Then: Liveness skipped (straight to result)
    // ────────────────────────────────────────

    @Then("the verification is performed without a liveness check")
    fun theVerificationIsPerformedWithoutALivenessCheck() {
        // Tap "Verify & Share" and expect result screen directly (no liveness screen in between)
        BddTestContext.tapConsentCta(rule)
        rule.waitForIdle()

        // Should NOT see liveness screen
        val livenessNodes = rule.onAllNodesWithTag("liveness_check_screen").fetchSemanticsNodes()
        assert(livenessNodes.isEmpty()) { "Expected NO liveness screen for low-value pack, but found one" }

        // Should see result screen
        rule.waitUntil(timeoutMillis = 10000) {
            rule.onAllNodesWithTag("verification_result").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Then("I see the Verification Result screen")
    fun iSeeTheVerificationResultScreen() {
        rule.onNodeWithTag("verification_result").assertIsDisplayed()
    }

    // ────────────────────────────────────────
    // Then: Liveness gate (screen appears)
    // ────────────────────────────────────────

    @Then("I see the Liveness Check screen")
    fun iSeeTheLivenessCheckScreen() {
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodesWithTag("liveness_check_screen").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("liveness_check_screen").assertIsDisplayed()
    }

    @Then("the screen explains why liveness is needed")
    fun theScreenExplainsWhyLivenessIsNeeded() {
        rule.onNodeWithText("Prove it\u2019s you", substring = true).assertIsDisplayed()
    }

    @Then("the front camera activates for the Veriff liveness session")
    fun theFrontCameraActivatesForVeriffSession() {
        // In demo mode we show a camera placeholder, not a real Veriff SDK
        rule.onNodeWithTag("liveness_camera_area").assertIsDisplayed()
    }

    // ────────────────────────────────────────
    // Given: Already on Liveness Check screen
    // ────────────────────────────────────────

    @Given("I am on the Liveness Check screen")
    fun iAmOnTheLivenessCheckScreen() {
        // Navigate to incoming request for a high-value pack, then tap Verify & Share
        iAmOnTheIncomingRequestScreenForPack("Childcare Readiness")
        BddTestContext.tapConsentCta(rule)
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodesWithTag("liveness_check_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ────────────────────────────────────────
    // When/Then: Liveness outcomes
    // ────────────────────────────────────────

    @When("the Veriff liveness check succeeds")
    fun theVeriffLivenessCheckSucceeds() {
        // In demo mode, tap "Simulate Pass" or auto-proceed
        DemoFixtures.livenessResult = DemoFixtures.LivenessResult.PASS
        rule.onNodeWithText("Simulate Pass", substring = true).performClick()
        rule.waitForIdle()
    }

    @When("the Veriff liveness check fails")
    fun theVeriffLivenessCheckFails() {
        // In demo mode, tap "Simulate Fail"
        DemoFixtures.livenessResult = DemoFixtures.LivenessResult.FAIL
        rule.onNodeWithText("Simulate Fail", substring = true).performClick()
        rule.waitForIdle()
    }

    @Then("the KB-JWT is signed and the presentation is sent")
    fun theKBJWTIsSignedAndPresentationSent() {
        // In demo mode, just verify we reach the result screen
        rule.waitUntil(timeoutMillis = 10000) {
            rule.onAllNodesWithTag("verification_result").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Then("the KB-JWT is not signed")
    fun theKBJWTIsNotSigned() {
        // Verify we did NOT reach the result screen
        val resultNodes = rule.onAllNodesWithTag("verification_result").fetchSemanticsNodes()
        assert(resultNodes.isEmpty()) { "Expected no verification result, but KB-JWT was signed" }
    }

    @Then("I see a liveness failure message")
    fun iSeeALivenessFailureMessage() {
        rule.onNodeWithText("couldn\u2019t confirm", substring = true).assertIsDisplayed()
    }

    @Then("I can retry the liveness check or cancel")
    fun iCanRetryOrCancel() {
        rule.onNodeWithText("Try Again").assertIsDisplayed()
        val cancelNode = rule.onNodeWithText("Cancel", substring = true)
        try { cancelNode.performScrollTo() } catch (_: Throwable) {}
        cancelNode.assertIsDisplayed()
    }

    @Then("I return to the Incoming Request screen")
    fun iReturnToTheIncomingRequestScreen() {
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodesWithTag("incoming_request_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Then("no credentials are shared")
    fun noCredentialsAreShared() {
        // Verify we're NOT on the result screen
        val resultNodes = rule.onAllNodesWithTag("verification_result").fetchSemanticsNodes()
        assert(resultNodes.isEmpty()) { "Credentials were shared when they should not have been" }
    }

    @Then("the verification completes without liveness")
    fun theVerificationCompletesWithoutLiveness() {
        // "Verify & Share" was already tapped by a prior When step.
        // Just assert: no liveness screen appeared, and result screen is visible.
        rule.waitForIdle()
        val livenessNodes = rule.onAllNodesWithTag("liveness_check_screen").fetchSemanticsNodes()
        assert(livenessNodes.isEmpty()) { "Expected NO liveness screen for low-value pack" }
        rule.waitUntil(timeoutMillis = 10000) {
            rule.onAllNodesWithTag("verification_result").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────

    private fun packDisplayName(pack: id.cachet.wallet.android.ui.model.CachPackUi): String =
        when (pack.cachetType) {
            id.cachet.wallet.android.ui.components.CachetType.CHILDCARE -> "Childcare Readiness"
            id.cachet.wallet.android.ui.components.CachetType.SELLER -> "Safe Seller"
            id.cachet.wallet.android.ui.components.CachetType.AGE -> "Age Verification"
            id.cachet.wallet.android.ui.components.CachetType.IDENTITY -> "Identity Verification"
        }
}
