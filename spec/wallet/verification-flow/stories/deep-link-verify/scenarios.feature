@story:deep-link-verify @domain:wallet/verification-flow @priority:high @status:draft
Feature: Deep Link Verification
  As a returning-holder
  I want to open a cachet:// deep link and see the verification request
  So that I can respond to a verifier without scanning a QR code

  Context:
    A verifier shares a cachet://verify link (via messaging, email, or web).
    Tapping the link opens the wallet directly on the Incoming Request screen
    for the specified Trust Pack. The holder reviews disclosures and consents
    or declines — same flow as QR, different entry point.

  Out of scope:
    - QR scanning (covered by scan-to-verify)
    - NFC tap verification
    - Generating deep links (verifier side)

  Background:
    Given the app is launched
    And the "happy" demo scenario is loaded

  # AC-1: Deep link opens incoming request
  @wireframe:cachet-03-incoming-request.svg
  Scenario Outline: Receiving a deep link for <pack> verification
    Given I have a valid identity cachet
    When I open the deep link "cachet://verify?request_uri=<request_uri>&pack=<pack>"
    Then I see the Incoming Request screen
    And the requested Trust Pack is "<pack_name>"

    Examples:
      | pack       | request_uri                  | pack_name        |
      | childcare  | http://10.0.2.2:8090/relay/1 | Childcare Ready  |
      | seller     | http://10.0.2.2:8090/relay/2 | Trusted Seller   |

  # AC-2: Deep link while app is in foreground
  @wireframe:cachet-03-incoming-request.svg
  Scenario: Deep link arrives while app is already open
    Given the app is in the foreground on the "My Cachets" tab
    When I open the deep link "cachet://verify?request_uri=http://10.0.2.2:8090/relay/1&pack=childcare"
    Then I see the Incoming Request screen
    And the requested Trust Pack is "Childcare Ready"

  # AC-3: Deep link with invalid or expired session
  @wireframe:cachet-04-deep-link-expired.svg
  Scenario: Deep link with expired session
    When I open the deep link "cachet://verify?request_uri=http://10.0.2.2:8090/relay/expired"
    Then I see an error message indicating the session is unavailable
    And I am returned to the vault screen

  # AC-4: Consent flow is identical to QR
  @wireframe:cachet-03-incoming-request.svg
  Scenario Outline: Consent decision from deep link — <decision>
    Given I arrived via a deep link and I am on the Incoming Request screen
    When I tap "<button>"
    Then <outcome>

    Examples:
      | decision | button         | outcome                                               |
      | accept   | Verify & Share | the verification is performed and I see the Verification Result screen |
      | decline  | Decline        | I return to the vault screen and no credentials are shared             |

  # AC-5: Deep link without identity cachet
  @wireframe:holder-05-empty-vault.svg
  Scenario: Deep link received with no identity cachet
    Given I have no credentials in my vault
    When I open the deep link "cachet://verify?request_uri=http://10.0.2.2:8090/relay/1&pack=childcare"
    Then I see a message that identity verification is required first
    And I am offered to start the identity verification flow
