@story:cachet-detail @domain:wallet/credentials @priority:high @status:draft
Feature: Cachet Detail
  As a returning-holder
  I want to see the full details of a cachet
  So that I understand what it proves and how it's secured

  Context:
    Reached by tapping a cachet card in My Cachets.
    Shows predicates, metadata, freshness, hardware indicator.

  Out of scope:
    - Editing or deleting a credential
    - Sharing from detail screen

  Background:
    Given the app is launched in demo mode
    And the "happy" demo scenario is loaded

  # AC-1: Prominent display
  @wireframe:cachet-01-detail.svg
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
  @wireframe:cachet-01-detail-hardware.svg
  Scenario Outline: Hardware-backed indicator visibility
    Given the credential <has_hardware> a hardware-backed signing key
    When I view its cachet detail
    Then I <see_indicator> the hardware-backed security indicator

    Examples:
      | has_hardware | see_indicator |
      | has          | see           |
      | does not have | do not see   |

  # AC-5: Freshness status
  Scenario: Freshness status is shown
    Given I am viewing a cachet detail
    Then I see the credential freshness status

  # AC-6: Back navigation
  Scenario: Returning to vault
    Given I am viewing a cachet detail
    When I press back
    Then I am on the "My Cachets" tab
