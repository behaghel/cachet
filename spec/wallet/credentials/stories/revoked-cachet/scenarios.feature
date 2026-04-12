Feature: Revoked Cachet
  As a revoked-holder
  I want to clearly understand that a credential has been revoked
  So that I know my next steps

  Background:
    Given the app is launched in demo mode
    And the "revoked" demo scenario is loaded

  # AC-1: Visual distinction in vault
  Scenario: Revoked card in vault is visually distinct
    When I am on the "My Cachets" tab
    Then the revoked cachet card has muted colors
    And the revoked cachet card shows a revoked badge

  # AC-6: Active cachets unaffected
  Scenario: Active cachets remain normal alongside revoked
    When I am on the "My Cachets" tab
    Then active cachet cards retain their normal visual treatment
    And only the revoked cachet card is visually muted

  # AC-2: Revocation banner in detail
  Scenario: Revoked detail shows revocation banner
    Given I am on the "My Cachets" tab
    When I tap the revoked cachet card
    Then I see a revocation banner at the top of the detail screen
    And the banner shows the revocation reason when available

  # AC-3: Original predicates visible but invalid
  Scenario: Predicates shown as no longer valid
    Given I am viewing the revoked cachet detail
    Then the original predicates are still visible
    And each predicate is marked as no longer valid

  # AC-4: Re-acquisition CTA
  Scenario: Re-acquire credential from revoked detail
    Given I am viewing the revoked cachet detail
    When I tap the re-acquire action
    Then I am navigated to the credential acquisition flow

  # AC-5: Status from StatusList2021
  Scenario: Revocation status determined by StatusList2021
    Given a credential with a StatusList2021 entry
    When the status list indicates revocation
    Then the credential is displayed as revoked in the vault
    And the detail screen shows the revocation banner

  # Wireframe: cachet-01-detail-revoked.svg
  Scenario: Visual match — revoked detail
    Given I am viewing the revoked cachet detail
    Then the screen matches wireframe "cachet-01-detail-revoked.svg"

  # Wireframe: holder-04-vault-revoked.svg
  Scenario: Visual match — vault with revoked card
    When I am on the "My Cachets" tab
    Then the screen matches wireframe "holder-04-vault-revoked.svg"
