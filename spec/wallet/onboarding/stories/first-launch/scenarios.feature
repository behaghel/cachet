@story:first-launch @domain:wallet/onboarding @priority:high @status:draft
Feature: First Launch
  As a first-time-user
  I want to understand what Cachet does and how it empowers me
  So that I feel confident to start using the app

  Context:
    App launches to onboarding when no credentials exist and onboarding not completed.
    Four screens: demand trust, prove yourself, your rules, get started.

  Out of scope:
    - Skip/dismiss onboarding early
    - Localization of onboarding content

  # AC-1: Onboarding shown on fresh install
  @wireframe:holder-01-onboarding-1.svg
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
  @wireframe:holder-01-onboarding-1.svg @wireframe:holder-02-onboarding-2.svg
  @wireframe:holder-03-onboarding-3.svg @wireframe:holder-04-onboarding-4.svg
  Scenario Outline: Onboarding screen conveys value proposition
    Given I am on onboarding screen <screen>
    Then the screen conveys "<message>"

    Examples:
      | screen | message                              |
      | 1      | demand proof from others on your terms |
      | 2      | prove yourself without over-sharing data |
      | 3      | your trust, your rules                |
      | 4      | get started                           |

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
  Scenario Outline: Step indicator shows progress
    Given I am on onboarding screen <screen>
    Then the step indicator shows "<screen> of 4"

    Examples:
      | screen |
      | 1      |
      | 2      |
      | 3      |
      | 4      |
