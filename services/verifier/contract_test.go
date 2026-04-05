package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/stretchr/testify/require"
)

const contractDir = "../../testdata/contracts"

// TestGenerateContractJSON writes golden JSON files from Go structs.
// The Kotlin tests read these files and verify they deserialize correctly.
// If a Go field is renamed or its type changes, the Kotlin test breaks.
//
// Run with: go test -run TestGenerateContract -v
func TestGenerateContractJSON(t *testing.T) {
	require.NoError(t, os.MkdirAll(contractDir, 0o755))

	t.Run("verify_response_cachet_granted", func(t *testing.T) {
		reason := "no credential from an accepted issuer"
		resp := models.VerifyResponse{
			Cachet:    "Childcare-Ready (ES)",
			Freshness: "ok",
			Predicates: []string{
				"age.ge.18",
				"identity.verified",
			},
			PredicateResults: []models.PredicateResult{
				{PredicateId: "age.ge.18", Status: models.Satisfied},
				{PredicateId: "identity.verified", Status: models.Satisfied},
				{PredicateId: "criminal.clear.es", Status: models.NoCredential, Reason: &reason},
			},
			Summary: models.VerificationSummary{
				CachetGranted:     true,
				RequiredSatisfied: 2,
				RequiredTotal:     2,
				OptionalSatisfied: intPtr(0),
				OptionalTotal:     intPtr(1),
			},
		}
		writeContract(t, "verify_response_granted.json", resp)
	})

	t.Run("verify_response_no_cachet", func(t *testing.T) {
		reason1 := "expected >= 18, got 0"
		reason2 := "no credential from an accepted issuer"
		resp := models.VerifyResponse{
			Cachet:    "",
			Freshness: "ok",
			// Go nil slice serializes as null — this is the bug that broke Kotlin
			Predicates: nil,
			PredicateResults: []models.PredicateResult{
				{PredicateId: "age.ge.18", Status: models.Failed, Reason: &reason1},
				{PredicateId: "identity.verified", Status: models.Satisfied},
				{PredicateId: "criminal.clear.es", Status: models.NoCredential, Reason: &reason2},
			},
			Summary: models.VerificationSummary{
				CachetGranted:     false,
				RequiredSatisfied: 1,
				RequiredTotal:     3,
			},
		}
		writeContract(t, "verify_response_no_cachet.json", resp)
	})

	t.Run("verify_response_empty_results", func(t *testing.T) {
		resp := models.VerifyResponse{
			Cachet:           "",
			Freshness:        "ok",
			Predicates:       nil,
			PredicateResults: []models.PredicateResult{},
			Summary: models.VerificationSummary{
				CachetGranted:     false,
				RequiredSatisfied: 0,
				RequiredTotal:     0,
			},
		}
		writeContract(t, "verify_response_empty.json", resp)
	})

	t.Run("verification_session", func(t *testing.T) {
		sessJSON := map[string]interface{}{
			"sessionId":       "test-session-123",
			"nonce":           "dGVzdC1ub25jZQ",
			"verifierDid":     "did:web:verifier.cachet.id",
			"ephemeralPubKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
			"requestObject":   "eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDp3ZWI6dmVyaWZpZXIuY2FjaGV0LmlkI2tleS0xIiwidHlwIjoib2F1dGgtYXV0aHotcmVxK2p3dCJ9.eyJjbGllbnRfaWQiOiJkaWQ6d2ViOnZlcmlmaWVyLmNhY2hldC5pZCJ9.fake-sig",
		}
		writeContract(t, "verification_session.json", sessJSON)
	})

	t.Run("verification_session_no_ephemeral", func(t *testing.T) {
		// Backward compat: session without ephemeral key
		sessJSON := map[string]interface{}{
			"sessionId":   "old-session-456",
			"nonce":       "b2xkLW5vbmNl",
			"verifierDid": "did:web:verifier.cachet.id",
		}
		writeContract(t, "verification_session_no_ephemeral.json", sessJSON)
	})

	t.Run("webhook_payload", func(t *testing.T) {
		// The webhook uses the Go Session struct's JSON tags (camelCase)
		webhook := map[string]interface{}{
			"session_id": "veriff-session-789",
			"status":     "approved",
			"person": map[string]interface{}{
				"firstName":   "Jane",
				"lastName":    "Doe",
				"dateOfBirth": "1995-06-15",
				"confidence":  0.96,
			},
			"document": map[string]interface{}{
				"number":       "AB1234567",
				"type":         "PASSPORT",
				"country":      "EE",
				"authenticity": 0.97,
			},
			"verification": map[string]interface{}{
				"livenessScore":     0.93,
				"overallConfidence": 0.96,
				"riskScore":         0.03,
				"timestamp":         "2026-04-05T00:00:00Z",
			},
		}
		writeContract(t, "webhook_payload.json", webhook)
	})

	t.Run("error_response", func(t *testing.T) {
		// Error responses have a different shape — Kotlin must not try to parse as VerifyResponse
		errResp := map[string]interface{}{
			"error":   "verification_failed",
			"message": "Credential verification failed: some reason",
		}
		writeContract(t, "error_response.json", errResp)
	})

	t.Run("credential_request_with_proof", func(t *testing.T) {
		// The proof.jwk must be a nested JSON object, not a string
		credReq := map[string]interface{}{
			"format": "vc+sd-jwt",
			"types":  []string{"VerifiableCredential", "IdentityCredential"},
			"proof": map[string]interface{}{
				"jwk": map[string]interface{}{
					"kty": "EC",
					"crv": "P-256",
					"x":   "test-x-coordinate",
					"y":   "test-y-coordinate",
				},
			},
		}
		writeContract(t, "credential_request_with_proof.json", credReq)
	})

	t.Run("relay_session", func(t *testing.T) {
		relaySess := map[string]string{
			"sessionId":   "relay-session-abc",
			"requestUri":  "/sessions/relay-session-abc/request",
			"responseUri": "/sessions/relay-session-abc/response",
		}
		writeContract(t, "relay_session.json", relaySess)
	})
}

func writeContract(t *testing.T, filename string, v interface{}) {
	t.Helper()
	data, err := json.MarshalIndent(v, "", "  ")
	require.NoError(t, err)
	path := filepath.Join(contractDir, filename)
	require.NoError(t, os.WriteFile(path, data, 0o644))
	t.Logf("wrote %s (%d bytes)", path, len(data))
}

func intPtr(i int) *int { return &i }
