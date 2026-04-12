Feature: Scan to Verify
  As a returning-holder
  I want to scan a verifier's QR code and review the verification request
  So that I can decide whether to share my credentials

  Background:
    Given the app is launched in demo mode
    And the "happy" demo scenario is loaded

  # AC-1: Camera viewfinder
  Scenario: Opening QR scanner
    Given I am on the "Activity" tab
    When I tap the FAB and select "Scan"
    Then the QR scanner opens with the camera viewfinder

  # AC-2: Scanning valid QR
  Scenario: Scanning a valid verifier QR
    Given the QR scanner is open
    When I scan a valid verifier QR code
    Then I see the Incoming Request screen

  # AC-3: Request details shown
  Scenario: Incoming request shows verification details
    Given I have scanned a verifier QR code
    When I am on the Incoming Request screen
    Then I see the verifier name
    And I see the requested Trust Pack
    And I see the required disclosures

  # AC-4: Disclosure types visible
  Scenario: Disclosures show their type
    Given I am on the Incoming Request screen
    Then each disclosure is listed with its type
    And the type indicates whether it is selective, always, or never disclosed

  # AC-5: Consent — accept
  Scenario: Accepting verification request
    Given I am on the Incoming Request screen
    When I tap "Verify & Share"
    Then the verification is performed
    And I see the Verification Result screen

  # AC-5: Consent — decline
  Scenario: Declining verification request
    Given I am on the Incoming Request screen
    When I tap "Decline"
    Then I return to the Activity tab
    And no credentials are shared

  # AC-6: Invalid QR handling
  Scenario: Scanning invalid QR
    Given the QR scanner is open
    When I scan an invalid QR code
    Then I see an error message
    And the scanner remains open for retry

  # Wireframe: cachet-02-qr-scan.svg
  Scenario: Visual match — QR scanner
    Given the QR scanner is open
    Then the screen matches wireframe "cachet-02-qr-scan.svg"

  # Wireframe: cachet-03-incoming-request.svg
  Scenario: Visual match — incoming request
    Given I am on the Incoming Request screen
    Then the screen matches wireframe "cachet-03-incoming-request.svg"
