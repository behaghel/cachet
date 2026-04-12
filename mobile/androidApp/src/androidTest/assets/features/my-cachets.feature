Feature: My Cachets
  As a returning-holder
  I want to see all my cachets at a glance
  So that I know what trust I hold and can act on it

  Background:
    Given the app is launched in demo mode

  # AC-1, AC-2: Vault displays credential cards with status
  Scenario: Viewing populated vault
    Given the "happy" demo scenario is loaded
    When I am on the "My Cachets" tab
    Then I see cachet cards for each stored credential
    And each card shows the cachet name, badge icon, and trust status

  # AC-3: Tapping navigates to detail
  Scenario: Tapping a cachet card opens detail
    Given the "happy" demo scenario is loaded
    And I am on the "My Cachets" tab
    When I tap on a cachet card
    Then I am navigated to the Cachet Detail screen

  # AC-4: Empty vault state
  Scenario: Viewing empty vault
    Given the "empty" demo scenario is loaded
    When I am on the "My Cachets" tab
    Then I see an empty state illustration
    And I see a "Get your first cachet" call to action

  # AC-5: FAB to acquire new cachet
  Scenario: Acquiring a new cachet from vault
    Given I am on the "My Cachets" tab
    When I tap the floating action button
    Then I am navigated to the Pack Picker in holder mode

  # AC-6: Tab switching
  Scenario: Switching to Activity tab
    Given I am on the "My Cachets" tab
    When I tap the "Activity" segment
    Then I am on the "Activity" tab

  # Wireframe: holder-04-vault-my-trust.svg
  Scenario: Visual match — populated vault
    Given the "happy" demo scenario is loaded
    And I am on the "My Cachets" tab
    Then the screen matches wireframe "holder-04-vault-my-trust.svg"

  # Wireframe: holder-05-empty-vault.svg
  Scenario: Visual match — empty vault
    Given the "empty" demo scenario is loaded
    And I am on the "My Cachets" tab
    Then the screen matches wireframe "holder-05-empty-vault.svg"
