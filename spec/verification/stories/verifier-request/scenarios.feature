Feature: Verifier Request
  As a verifier
  I want to select a Trust Pack and generate a QR code
  So that a holder can scan it and share their credentials with me

  Background:
    Given the app is launched in demo mode

  # AC-1: Access from FAB
  Scenario: Starting a new verification request
    Given I am on the "Activity" tab
    When I tap the FAB and select "New request"
    Then I am on the Pack Picker screen in verifier mode

  # AC-2: Same packs available
  Scenario: Verifier sees same packs as holder
    Given I am on the Pack Picker screen in verifier mode
    Then I see the same Trust Packs available to holders

  # AC-3: Selecting a pack generates QR
  Scenario: Selecting a pack shows QR code
    Given I am on the Pack Picker screen in verifier mode
    When I tap on a Trust Pack
    Then a verification session is created
    And I see the Show QR screen with a scannable QR code

  # AC-4: QR screen details
  Scenario: QR screen shows session information
    Given I am on the Show QR screen
    Then I see the selected pack name
    And I see the session status as "Waiting"

  # AC-5: QR encodes session URL
  Scenario: QR contains valid session data
    Given I am on the Show QR screen
    Then the QR code encodes a session URL
    And a holder's scanner can decode and process it

  # AC-6: Automatic result on completion
  Scenario: Verifier receives result when holder completes
    Given I am on the Show QR screen
    And the session status is "Waiting"
    When a holder scans and completes the verification
    Then the session status updates
    And I see the Verification Result screen

  # Wireframe: verify-01-new-request.svg
  Scenario: Visual match — new request
    Given I am on the Pack Picker screen in verifier mode
    Then the screen matches wireframe "verify-01-new-request.svg"

  # Wireframe: verify-02-show-qr.svg
  Scenario: Visual match — show QR
    Given I am on the Show QR screen
    Then the screen matches wireframe "verify-02-show-qr.svg"
