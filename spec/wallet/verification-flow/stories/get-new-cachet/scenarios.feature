@story:get-new-cachet @domain:wallet/verification-flow @priority:high @status:draft
Feature: Get New Cachet
  As a first-time-user or returning-holder
  I want to acquire a new cachet
  So that I can prove something about myself

  Context:
    Two entry points with different flows:
    - Empty vault (no identity cachet): CTA launches Veriff identity verification
      directly — identity is the prerequisite for all other cachets.
    - FAB (has identity cachet): opens Pack Picker to choose additional cachets.

  Out of scope:
    - Pack search/filter
    - Pack comparison

  Background:
    Given the app is launched in demo mode

  # AC-1: Empty vault starts identity verification, not pack selection
  Scenario: First cachet is always identity via Veriff
    Given the "empty" demo scenario is loaded
    And I am on the empty vault screen
    When I tap "Get your first cachet"
    Then the identity verification flow begins
    And I do not see the Pack Picker screen

  # AC-2: Pack picker requires identity cachet
  Scenario: Pack picker reachable only with identity cachet
    Given the "happy" demo scenario is loaded
    And I am on the "My Cachets" tab
    When I tap the floating action button
    Then I am on the Pack Picker screen in holder mode

  # AC-3: Pack list from registry
  @wireframe:holder-06-pick-pack.svg
  Scenario: Viewing available packs
    Given I am on the Pack Picker screen in holder mode
    Then I see all available Trust Packs from the registry

  # AC-4: Pack card details
  Scenario: Pack card shows key information
    Given I am on the Pack Picker screen in holder mode
    Then each pack card shows the pack name
    And each pack card shows a description
    And each pack card shows the required verification type

  # AC-5: Selecting a pack starts acquisition
  Scenario: Tapping a pack starts credential acquisition
    Given I am on the Pack Picker screen in holder mode
    When I tap on a Trust Pack
    Then the credential acquisition flow begins

  # AC-6: Cancel and return
  Scenario: Cancelling pack selection
    Given I am on the Pack Picker screen in holder mode
    When I press back
    Then I return to the vault screen
