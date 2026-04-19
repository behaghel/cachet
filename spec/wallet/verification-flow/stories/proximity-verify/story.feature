@story:proximity-verify @domain:wallet/verification-flow @priority:high @status:draft
Feature: Proximity Verification (In-Person)
  As a verifier or holder
  I want to verify credentials face-to-face without internet
  So that I can establish trust in offline environments

  Context:
    Two co-present devices exchange a verification request and response
    via QR codes. No relay, no network. Both devices can be in airplane mode.
    The verifier shows a session QR; the holder scans it, consents, and
    shows a response QR; the verifier scans it and verifies locally.

  Out of scope:
    - BLE transport (Phase 3)
    - NFC tap initiation (Phase 4)
    - Chunked QR for large payloads (Phase 2)

  Background:
    Given the app is launched in demo mode
    And the "happy" demo scenario is loaded

  # ── Verifier side ──

  # AC-1: Initiate in-person verification
  Scenario: Verifier selects in-person verification mode
    Given I am on the "Activity" tab
    When I tap the FAB and select "Verify"
    And I toggle "In person" mode
    And I select the "Childcare Readiness" pack
    Then I see the Proximity QR screen
    And a QR code is displayed containing session parameters

  # AC-2: Session QR contains required parameters
  Scenario: Proximity QR encodes all session parameters
    Given I am on the Proximity QR screen
    Then the QR payload starts with "cachet://proximity?"
    And the QR payload contains a nonce parameter "n"
    And the QR payload contains an ephemeral public key parameter "vk"
    And the QR payload contains a pack ID parameter "pack"
    And the QR payload contains a question parameter "q"

  # AC-3: Verifier scans holder's response
  Scenario: Verifier scans response QR and sees result
    Given I am on the Proximity QR screen
    When I tap "Scan response"
    And I scan a valid proximity VP QR code
    Then I see the Verification Result screen
    And the cachet is granted

  # AC-4: Verifier scans invalid response
  Scenario: Verifier scans non-VP QR
    Given I am on the Proximity QR screen
    When I tap "Scan response"
    And I scan a QR code that is not a proximity VP
    Then I see an error message
    And the scanner remains open for retry

  # ── Holder side ──

  # AC-5: Holder scans proximity QR
  Scenario: Holder scans verifier's proximity QR
    Given the QR scanner is open
    When I scan a proximity session QR code
    Then I see the Incoming Request screen
    And I see the requested Trust Pack
    And I see the required disclosures

  # AC-6: Holder consent and response display
  Scenario Outline: Holder consent decision in proximity mode — <decision>
    Given I have scanned a proximity session QR code
    And I am on the Incoming Request screen
    When I tap "<button>"
    Then <outcome>

    Examples:
      | decision | button         | outcome                                                                    |
      | accept   | Verify & Share | I see the Proximity Response screen with a QR code containing my encrypted VP |
      | decline  | Decline        | I return to the Activity tab and no credentials are shared                  |

  # AC-7: Response QR format
  Scenario: Response QR contains encrypted VP
    Given I have consented to a proximity verification request
    When I am on the Proximity Response screen
    Then the displayed QR payload starts with "cachet-vp:"
    And the payload is a valid JWE compact serialization

  # ── Offline guarantees ──

  # AC-8: Full flow in airplane mode
  Scenario: End-to-end proximity verification offline
    Given both devices have airplane mode enabled
    And the holder has a valid cached identity credential
    And the verifier has cached pack definitions and DID documents
    When the verifier initiates in-person verification
    And the holder scans the session QR
    And the holder consents and displays the response QR
    And the verifier scans the response QR
    Then the verifier sees the Verification Result screen
    And the cachet is granted

  # ── Error scenarios ──

  # AC-9: VP too large for QR
  Scenario: Holder's VP exceeds QR capacity
    Given I have scanned a proximity session QR code
    And my credential VP would exceed 2500 bytes when encrypted
    When I tap "Verify & Share"
    Then I see a message saying the credential is too large for in-person verification
    And I am offered to use online verification instead
