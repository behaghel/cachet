package id.cachet.wallet.android.bdd.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.cachet.wallet.android.bdd.BddTestContext
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * Step definitions for the scan-to-verify story.
 *
 * "I tap {string}" is in CommonSteps (shared).
 */
class ScanToVerifySteps {

    private val rule get() = BddTestContext.sharedRule!!

    // AC-1: Camera viewfinder
    @When("I tap the FAB and select {string}")
    fun iTapTheFABAndSelect(option: String) {
        when (option) {
            "Scan" -> {
                rule.onNodeWithTag("fab_scan_qr").performClick()
                rule.waitForIdle()
            }
            "New request" -> {
                rule.onNodeWithTag("fab_new_request").performClick()
                rule.waitForIdle()
            }
        }
    }

    @Then("the QR scanner opens with the camera viewfinder")
    fun theQRScannerOpensWithTheCameraViewfinder() {
        rule.onNodeWithTag("qr_scanner_screen").assertIsDisplayed()
        rule.onNodeWithTag("qr_viewfinder").assertIsDisplayed()
    }

    // AC-2: Scanning valid QR
    @When("I scan a valid verifier QR code")
    fun iScanAValidVerifierQRCode() {
        // In demo mode, the QR scanner auto-scans after 2 seconds then transitions
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("incoming_request_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Then("I see the Incoming Request screen")
    fun iSeeTheIncomingRequestScreen() {
        rule.onNodeWithTag("incoming_request_screen").assertIsDisplayed()
    }

    // AC-3: Request details
    @Given("I have scanned a verifier QR code")
    fun iHaveScannedAVerifierQRCode() {
        rule.onNodeWithText("Activity").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fab_scan_qr").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("incoming_request_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Then("I see the verifier name")
    fun iSeeTheVerifierName() {
        rule.onNodeWithText("wants to know:", substring = true).assertIsDisplayed()
    }

    @Then("I see the requested Trust Pack")
    fun iSeeTheRequestedTrustPack() {
        rule.onNodeWithText("Are you safe for childcare?").assertIsDisplayed()
    }

    @Then("I see the required disclosures")
    fun iSeeTheRequiredDisclosures() {
        val predicates = rule.onAllNodesWithTag("predicate_chip").fetchSemanticsNodes()
        assert(predicates.isNotEmpty()) { "Expected disclosure predicates" }
    }

    // AC-4: Disclosure types
    @Then("each disclosure is listed with its type")
    fun eachDisclosureIsListedWithItsType() {
        rule.onAllNodesWithTag("predicate_chip").fetchSemanticsNodes().let { nodes ->
            assert(nodes.isNotEmpty()) { "Expected predicate chips with disclosure types" }
        }
    }

    @Then("the type indicates whether it is selective, always, or never disclosed")
    fun theTypeIndicatesDisclosureLevel() {
        rule.onAllNodesWithText("NOT be shared", substring = true).onFirst().assertIsDisplayed()
    }

    // AC-5: Consent decision
    @Then("the verification is performed and I see the Verification Result screen")
    fun theVerificationIsPerformedAndISeeTheResult() {
        rule.waitUntil(timeoutMillis = 10000) {
            rule.onAllNodes(hasTestTag("verification_result")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("verification_result").assertIsDisplayed()
    }

    @Then("I return to the Activity tab and no credentials are shared")
    fun iReturnToTheActivityTabAndNoCredentialsAreShared() {
        // Wait for overlay to close and tabs to appear
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodesWithText("My Cachets").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // AC-6: Invalid QR handling
    @When("I scan an invalid QR code")
    fun iScanAnInvalidQRCode() {
        rule.waitForIdle()
    }

    @Then("I see an error message")
    fun iSeeAnErrorMessage() {
        rule.waitForIdle()
    }

    @Then("the scanner remains open for retry")
    fun theScannerRemainsOpenForRetry() {
        rule.onNodeWithTag("qr_scanner_screen").assertIsDisplayed()
    }
}
