Feature: Verification Result
  As a returning-holder or verifier
  I want to see the outcome of a verification
  So that I know whether credentials passed or failed and why

  Background:
    Given the app is launched in demo mode

  # AC-1: Pass result
  Scenario: Viewing a pass result
    Given a verification has completed successfully
    When I am on the Verification Result screen
    Then I see a green success state
    And I see the cachet badge for the verified pack

  # AC-2: Fail result
  Scenario: Viewing a fail result
    Given a verification has completed with failures
    When I am on the Verification Result screen
    Then I see a red failure state
    And I see a clear reason for the failure

  # AC-3: Individual predicate results
  Scenario: Predicates listed individually
    Given a verification has completed
    When I am on the Verification Result screen
    Then each predicate result is listed individually
    And each predicate shows pass or fail status

  # AC-4: Pack identification
  Scenario: Result shows which pack was verified
    Given a verification has completed
    When I am on the Verification Result screen
    Then I see the name of the Trust Pack that was verified against

  # AC-6: Dismiss and return
  Scenario: Dismissing the result
    Given I am on the Verification Result screen
    When I dismiss the result
    Then I return to the Activity tab

  # AC-7: Consent receipt generated
  Scenario: Consent receipt is generated
    Given a verification has completed
    Then a consent receipt is generated and stored
    And the receipt appears in the Activity feed

  # Demo scenario: happy — age pass
  Scenario: Age verification pass
    Given the "happy" demo scenario is loaded
    And I pick the Age Verification pack
    And I complete the verification flow
    When I am on the Verification Result screen
    Then I see a pass result for "Age Verified"
    And I see the age predicate passed

  # Demo scenario: seller-only — fail
  Scenario: Seller verification fail
    Given the "seller-only" demo scenario is loaded
    And I pick the Safe Seller pack
    And I complete the verification flow
    When I am on the Verification Result screen
    Then I see a fail result for "Safe Seller"
    And I see which seller predicates failed

  # Wireframe: cachet-04-result-pass.svg
  Scenario: Visual match — pass result
    Given a verification has completed successfully
    Then the screen matches wireframe "cachet-04-result-pass.svg"

  # Wireframe: cachet-04-result-pass-age.svg
  Scenario: Visual match — age pass result
    Given an age verification has completed successfully
    Then the screen matches wireframe "cachet-04-result-pass-age.svg"

  # Wireframe: cachet-05-result-fail.svg
  Scenario: Visual match — fail result
    Given a verification has completed with failures
    Then the screen matches wireframe "cachet-05-result-fail.svg"

  # Wireframe: cachet-05-result-fail-seller.svg
  Scenario: Visual match — seller fail result
    Given a seller verification has completed with failures
    Then the screen matches wireframe "cachet-05-result-fail-seller.svg"
