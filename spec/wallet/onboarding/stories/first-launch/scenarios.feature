Feature: First Launch
  As a first-time-user
  I want to understand what Cachet does and how it empowers me
  So that I feel confident to start using the app

  # AC-1: Onboarding shown on fresh install
  Scenario: Fresh install shows onboarding
    Given the app is launched for the first time
    Then I see the first onboarding screen
    And the headline is "Don't take their word for it"

  # AC-2: Advancing through screens
  Scenario: Navigating through onboarding
    Given I am on onboarding screen 1
    When I tap "Next"
    Then I am on onboarding screen 2
    When I tap "Next"
    Then I am on onboarding screen 3
    When I tap "Next"
    Then I am on onboarding screen 4

  # AC-3: Each screen has distinct value proposition
  Scenario: Screen 1 — demand trust
    Given I am on onboarding screen 1
    Then the screen conveys "demand proof from others on your terms"

  Scenario: Screen 2 — prove yourself
    Given I am on onboarding screen 2
    Then the screen conveys "prove yourself without over-sharing data"

  Scenario: Screen 3 — your rules
    Given I am on onboarding screen 3
    Then the screen conveys "your trust, your rules"

  Scenario: Screen 4 — get started
    Given I am on onboarding screen 4
    Then I see a "Get Started" call to action

  # AC-4: Transition to vault
  Scenario: Completing onboarding reaches empty vault
    Given I am on onboarding screen 4
    When I tap "Get Started"
    Then I am on the empty vault screen

  # AC-5: Onboarding only shown once
  Scenario: Second launch skips onboarding
    Given I have completed onboarding previously
    When I launch the app
    Then I am taken directly to the vault screen

  # AC-6: Step indicator
  Scenario: Step indicator shows progress
    Given I am on onboarding screen 2
    Then the step indicator shows "2 of 4"

  # Wireframe: holder-01-onboarding-1.svg
  Scenario: Visual match — onboarding screen 1
    Given I am on onboarding screen 1
    Then the screen matches wireframe "holder-01-onboarding-1.svg"

  # Wireframe: holder-02-onboarding-2.svg
  Scenario: Visual match — onboarding screen 2
    Given I am on onboarding screen 2
    Then the screen matches wireframe "holder-02-onboarding-2.svg"

  # Wireframe: holder-03-onboarding-3.svg
  Scenario: Visual match — onboarding screen 3
    Given I am on onboarding screen 3
    Then the screen matches wireframe "holder-03-onboarding-3.svg"

  # Wireframe: holder-04-onboarding-4.svg
  Scenario: Visual match — onboarding screen 4
    Given I am on onboarding screen 4
    Then the screen matches wireframe "holder-04-onboarding-4.svg"
