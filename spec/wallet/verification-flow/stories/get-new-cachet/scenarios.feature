@story:get-new-cachet @domain:wallet/verification-flow @priority:high @status:draft
Feature: Get New Cachet
  As a first-time-user or returning-holder
  I want to browse available Trust Packs and start the credential acquisition flow
  So that I can earn a new cachet

  Context:
    Pack picker in holder mode. Reachable from empty vault CTA and FAB.
    Selecting a pack initiates Veriff session or demo consent.

  Out of scope:
    - Pack search/filter
    - Pack comparison

  Background:
    Given the app is launched in demo mode

  # AC-1: Pack list from registry
  @wireframe:holder-06-pick-pack.svg
  Scenario: Viewing available packs
    Given I am on the Pack Picker screen in holder mode
    Then I see all available Trust Packs from the registry

  # AC-2: Pack card details
  Scenario: Pack card shows key information
    Given I am on the Pack Picker screen in holder mode
    Then each pack card shows the pack name
    And each pack card shows a description
    And each pack card shows the required verification type

  # AC-3: Selecting a pack starts acquisition
  Scenario: Tapping a pack starts credential acquisition
    Given I am on the Pack Picker screen in holder mode
    When I tap on a Trust Pack
    Then the credential acquisition flow begins

  # AC-4: Cancel and return
  Scenario: Cancelling pack selection
    Given I am on the Pack Picker screen in holder mode
    When I press back
    Then I return to the vault screen

  # AC-5: Reachable from multiple entry points
  Scenario Outline: Accessing pack picker from <entry_point>
    Given <precondition>
    When I <action>
    Then I am on the Pack Picker screen in holder mode

    Examples:
      | entry_point    | precondition                                                    | action                       |
      | My Cachets FAB | I am on the "My Cachets" tab                                   | tap the floating action button |
      | empty vault    | the "empty" demo scenario is loaded and I am on the empty vault screen | tap "Get your first cachet"  |
