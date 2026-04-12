Feature: Get New Cachet
  As a first-time-user or returning-holder
  I want to browse available Trust Packs and start the credential acquisition flow
  So that I can earn a new cachet

  Background:
    Given the app is launched in demo mode

  # AC-1: Pack list from registry
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

  # AC-5: Reachable from vault FAB
  Scenario: Accessing from My Cachets FAB
    Given I am on the "My Cachets" tab
    When I tap the floating action button
    Then I am on the Pack Picker screen in holder mode

  # AC-5: Reachable from empty vault CTA
  Scenario: Accessing from empty vault
    Given the "empty" demo scenario is loaded
    And I am on the empty vault screen
    When I tap "Get your first cachet"
    Then I am on the Pack Picker screen in holder mode

  # Wireframe: holder-06-pick-pack.svg
  Scenario: Visual match — pack picker
    Given I am on the Pack Picker screen in holder mode
    Then the screen matches wireframe "holder-06-pick-pack.svg"
