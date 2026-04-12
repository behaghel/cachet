Feature: Cachet Detail
  As a returning-holder
  I want to see the full details of a cachet
  So that I understand what it proves and how it's secured

  Background:
    Given the app is launched in demo mode
    And the "happy" demo scenario is loaded

  # AC-1: Prominent display
  Scenario: Viewing cachet detail
    Given I am on the "My Cachets" tab
    When I tap on a cachet card
    Then I see the cachet name prominently
    And I see the badge icon
    And I see the trust status

  # AC-2: Predicate listing
  Scenario: Predicates are listed with status
    Given I am viewing a cachet detail
    Then I see all predicates listed
    And each predicate shows its evaluation status

  # AC-3: Credential metadata
  Scenario: Metadata is displayed
    Given I am viewing a cachet detail
    Then I see the issuer name
    And I see the issuance date
    And I see the expiry date if present

  # AC-4: Hardware-backed indicator
  Scenario: Hardware-backed credential shows indicator
    Given the credential has a hardware-backed signing key
    When I view its cachet detail
    Then I see the hardware-backed security indicator

  Scenario: Software-backed credential has no hardware indicator
    Given the credential does not have a hardware-backed signing key
    When I view its cachet detail
    Then I do not see the hardware-backed security indicator

  # AC-5: Freshness status
  Scenario: Freshness status is shown
    Given I am viewing a cachet detail
    Then I see the credential freshness status

  # AC-6: Back navigation
  Scenario: Returning to vault
    Given I am viewing a cachet detail
    When I press back
    Then I am on the "My Cachets" tab

  # Wireframe: cachet-01-detail.svg
  Scenario: Visual match — standard detail
    Given I am viewing a cachet detail
    Then the screen matches wireframe "cachet-01-detail.svg"

  # Wireframe: cachet-01-detail-hardware.svg
  Scenario: Visual match — hardware-backed detail
    Given I am viewing a hardware-backed cachet detail
    Then the screen matches wireframe "cachet-01-detail-hardware.svg"
