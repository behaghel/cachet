@story:cachet-detail-v2 @domain:wallet/credentials @priority:must @status:draft
Feature: Behavioral Cachet Detail with Tier Dial
  As a returning-holder
  I want to see my cachet's strength, tier, and evidence breakdown
  So that I understand my trust profile and know how to improve it

  Context:
    Reached by tapping a behavioral cachet (Trusted Host, Safe Seller...)
    in My Cachets. Shows a C-shaped dial with the cachet logo inside,
    metallic tier badge, strength percentage, cachet name, predicates,
    and per-platform evidence breakdown with contribution bars.

    This is the v2 detail screen for behavioral cachets that have a
    TrustTrail evidence section. Identity cachets continue to use the
    v1 detail (cachet-detail story).

  Out of scope:
    - Identity cachet detail (uses v1 layout)
    - Evidence submission flow (separate story)
    - TrustTrail scan / platform discovery (separate story)
    - Tier degradation alerts (separate story)

  Background:
    Given the app is launched in demo mode
    And the holder has a behavioral cachet

  # AC-1: Tier dial hero
  @wireframe:cachet-01-detail-v2.svg
  Scenario: Viewing a behavioral cachet shows the tier dial
    Given I am on the "My Cachets" tab
    When I tap on a behavioral cachet card
    Then I see a C-shaped circular dial
    And the cachet shield logo is centered inside the dial
    And the dial is filled in green up to the current strength

  # AC-2: Tier badge and strength
  @wireframe:cachet-01-detail-v2.svg
  Scenario Outline: Tier badge reflects current strength
    Given the cachet strength is <strength>
    When I view the cachet detail
    Then I see the tier badge showing "<tier>"
    And I see the strength displayed as "<display>"

    Examples:
      | strength | tier   | display |
      | 0.35     | BRONZE | 35%     |
      | 0.72     | SILVER | 72%     |
      | 0.91     | GOLD   | 91%     |

  # AC-3: Cachet name below badge
  @wireframe:cachet-01-detail-v2.svg
  Scenario: Cachet name is prominent below the tier
    Given I am viewing a behavioral cachet detail
    Then I see the cachet name below the strength percentage
    And the name is the most prominent text label on the screen

  # AC-4: Metadata row
  @wireframe:cachet-01-detail-v2.svg
  Scenario: Metadata shows issuance and foundation link
    Given I am viewing a behavioral cachet detail
    Then I see the issuance date
    And I see the issuer as "Cachet"
    And I see the linked identity cachet status

  # AC-5: Predicates
  @wireframe:cachet-01-detail-v2.svg
  Scenario: Predicates list what the cachet proves
    Given I am viewing a behavioral cachet detail
    Then I see a "What this proves" section
    And each predicate shows a check mark and description
    And each predicate has a privacy note explaining what is not shared

  # AC-6: Evidence breakdown per platform
  @wireframe:cachet-01-detail-v2.svg
  Scenario: Evidence shows per-platform contribution
    Given I am viewing a behavioral cachet detail
    And the cachet has evidence from multiple platforms
    Then I see an "Evidence" section
    And each platform shows its name and evidence item count
    And each platform shows its contribution percentage
    And each platform has a progress bar proportional to its contribution

  # AC-7: Single platform evidence
  @wireframe:cachet-01-detail-v2.svg
  Scenario: Evidence with a single platform
    Given I am viewing a behavioral cachet detail
    And the cachet has evidence from one platform only
    Then I see one platform row showing 100% contribution

  # AC-8: Scan CTA
  @wireframe:cachet-01-detail-v2.svg
  Scenario Outline: Secondary CTA adapts to tier status
    Given the cachet tier is "<current_tier>"
    When I view the cachet detail
    Then I see a secondary button at the bottom with text "<cta_text>"

    Examples:
      | current_tier | cta_text                                |
      | BRONZE       | Scan for more evidence to reach Silver  |
      | SILVER       | Scan for more evidence to reach Gold    |
      | GOLD         | Scan to keep your Gold status fresh     |

  # AC-9: Back navigation
  @wireframe:cachet-01-detail-v2.svg
  Scenario: Returning to vault
    Given I am viewing a behavioral cachet detail
    When I press back
    Then I am on the "My Cachets" tab

  # AC-10: No tier yet
  @wireframe:cachet-01-detail-v2.svg
  Scenario: Cachet with strength below bronze
    Given the cachet strength is 0.15
    When I view the cachet detail
    Then the tier badge is not shown
    And the dial is filled to 15%
    And the secondary CTA says "Scan for more evidence to reach Bronze"
