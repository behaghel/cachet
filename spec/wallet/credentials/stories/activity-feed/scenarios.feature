@story:activity-feed @domain:wallet/credentials @priority:high @status:draft
Feature: Activity Feed
  As a returning-holder
  I want to see my verification history
  So that I know when and where my credentials were shared

  Context:
    Second tab on home screen. Shows chronological verification events.
    Tab switching is defined in @story:my-cachets (not duplicated here).

  Out of scope:
    - Filtering by date range or verifier
    - Activity search

  Background:
    Given the app is launched in demo mode
    And the "happy" demo scenario is loaded

  # AC-1: Chronological list
  @wireframe:activity-01-tab.svg
  Scenario: Viewing activity history
    When I am on the "Activity" tab
    Then I see a chronological list of verification events
    And the most recent event is at the top

  # AC-2: Entry details
  Scenario: Activity entry shows key information
    When I am on the "Activity" tab
    Then each entry shows the cachet name
    And each entry shows the date and time
    And each entry shows the direction indicator

  # AC-3: Direction indicator
  Scenario: Direction distinguishes shared from received
    When I am on the "Activity" tab
    Then outgoing verifications show a "shared" indicator
    And incoming verifications show a "received" indicator

  # AC-4: Tapping entry
  Scenario: Tapping activity entry shows detail
    Given I am on the "Activity" tab
    When I tap on an activity entry
    Then I see the consent receipt detail

  # AC-5: Empty state
  Scenario: Empty activity feed
    Given no verification events have occurred
    When I am on the "Activity" tab
    Then I see an empty state message
