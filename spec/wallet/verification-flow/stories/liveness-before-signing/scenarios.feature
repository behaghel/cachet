@story:liveness-before-signing @domain:wallet/verification-flow @priority:must @issue:58
Feature: Holder identity confirmation before signing high-value verifications
  As a returning-holder
  I want to prove I am the credential holder before my wallet signs a high-value verification response
  So that nobody else can use my phone to impersonate me for consequential checks

  Context:
    After the holder taps "Verify & Share" on the consent screen, the wallet
    checks the CachPack policy. High-value packs (e.g. Childcare Readiness)
    require a Veriff Biometric Authentication — a camera-based face match
    against the enrollment from the original identity verification at issuance
    — before the KB-JWT is signed and the presentation sent.
    Low-value packs (e.g. Age) skip straight to signing.

    This is NOT on-device biometrics (fingerprint/face unlock). Veriff
    Biometric Authentication combines passive liveness detection (anti-spoofing)
    with face matching against the holder's enrollment template, proving both
    that the person is real AND that they are the credential holder.

    The Veriff SDK owns the capture UX (selfie guidance, camera framing).
    Our screens are the before/after wrapper: explanation → SDK → result.

  Out of scope:
    - Verifier-forced identity confirmation (future: verifier request can override pack policy)
    - On-device biometrics (not needed — Veriff handles liveness + face match)
    - Liveness during issuance (separate flow, already handled by Veriff KYC)
    - Distinguishing failure reasons to the user (security: never reveal why match failed)

  Background:
    Given the app is launched in demo mode
    And I hold a valid credential

  # AC-1: Liveness required for high-value cachets
  @wireframe:cachet-03b-liveness-check.svg
  Scenario: High-value cachet triggers liveness check after consent
    Given I am on the Incoming Request screen for a "Childcare Readiness" pack
    When I tap "Verify & Share"
    Then I see the Liveness Check screen
    And the screen explains why liveness is needed
    And the front camera activates for the Veriff liveness session

  # AC-2: Liveness not required for low-value cachets
  @wireframe:cachet-03-incoming-request.svg @wireframe:cachet-04-result-pass.svg
  Scenario: Low-value cachet skips liveness check
    Given I am on the Incoming Request screen for an "Age Verification" pack
    When I tap "Verify & Share"
    Then the verification is performed without a liveness check
    And I see the Verification Result screen

  # AC-3: Successful liveness leads to signing and result
  @wireframe:cachet-03b-liveness-check.svg @wireframe:cachet-04-result-pass.svg
  Scenario: Liveness passes and verification completes
    Given I am on the Liveness Check screen
    When the Veriff liveness check succeeds
    Then the KB-JWT is signed and the presentation is sent
    And I see the Verification Result screen

  # AC-4: Failed liveness blocks signing
  @wireframe:cachet-03b-liveness-failed.svg
  Scenario: Liveness fails and signing is blocked
    Given I am on the Liveness Check screen
    When the Veriff liveness check fails
    Then the KB-JWT is not signed
    And I see a liveness failure message
    And I can retry the liveness check or cancel

  # AC-5: Holder cancels liveness
  @wireframe:cachet-03b-liveness-check.svg
  Scenario: Holder cancels liveness check
    Given I am on the Liveness Check screen
    When I tap "Cancel"
    Then the KB-JWT is not signed
    And I return to the Incoming Request screen
    And no credentials are shared

  # AC-6: Liveness requirement is per-CachPack
  Scenario Outline: Liveness requirement varies by CachPack — <pack>
    Given I am on the Incoming Request screen for a "<pack>" pack
    When I tap "Verify & Share"
    Then <outcome>

    Examples:
      | pack                  | outcome                                  |
      | Childcare Readiness   | I see the Liveness Check screen          |
      | Safe Seller           | I see the Liveness Check screen          |
      | Identity Verification | I see the Liveness Check screen          |
      | Age Verification      | the verification completes without liveness |
