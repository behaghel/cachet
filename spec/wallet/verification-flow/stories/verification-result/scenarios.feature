@story:verification-result @domain:wallet/verification-flow @priority:high @status:draft
Feature: Verification Result
  As a returning-holder or verifier
  I want to see the outcome of a verification
  So that I know whether credentials passed or failed and why

  Context:
    Shown after holder consents to share or verifier receives scan result.
    Pass = green + cachet badge. Fail = red + reason.
    Consent receipt generated for every verification.

  Out of scope:
    - Result history (covered by activity-feed)
    - Sharing or exporting result

  Background:
    Given the app is launched in demo mode

  # AC-1, AC-2: Pass/fail result display
  Scenario Outline: Viewing a <outcome> result
    Given a verification has completed with <outcome> outcome
    When I am on the Verification Result screen
    Then I see a <color> <outcome> state
    And I see <detail>

    Examples:
      | outcome | color | detail                                  |
      | pass    | green | the cachet badge for the verified pack   |
      | fail    | red   | a clear reason for the failure           |

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

  # Demo scenarios: pack-specific results
  @wireframe:cachet-04-result-pass.svg @wireframe:cachet-04-result-pass-age.svg
  @wireframe:cachet-05-result-fail.svg @wireframe:cachet-05-result-fail-seller.svg
  Scenario Outline: <pack> verification <outcome>
    Given the "<demo>" demo scenario is loaded
    And I pick the <pack> pack
    And I complete the verification flow
    When I am on the Verification Result screen
    Then I see a <outcome> result for "<label>"
    And I see <predicate_detail>

    Examples:
      | demo        | pack              | outcome | label         | predicate_detail                |
      | happy       | Age Verification  | pass    | Age Verified  | the age predicate passed        |
      | seller-only | Safe Seller       | fail    | Safe Seller   | which seller predicates failed  |
