package schema_integration

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestSchemaCompatibility validates that the backend API responses match the OpenAPI schema
func TestSchemaCompatibility(t *testing.T) {
	// Start test server (you'll need to implement this)
	server := startTestServer(t)
	defer server.Close()

	t.Run("OAuth Token Request/Response Schema", func(t *testing.T) {
		// Test TokenRequest schema compliance
		tokenRequest := map[string]interface{}{
			"grant_type": "client_credentials",
			"client_id":  "test-client",
			"scope":      "credential_issuance",
		}

		reqBody, err := json.Marshal(tokenRequest)
		require.NoError(t, err)

		resp, err := http.Post(server.URL+"/oauth/token", "application/json", bytes.NewBuffer(reqBody))
		require.NoError(t, err)
		defer resp.Body.Close()

		assert.Equal(t, http.StatusOK, resp.Status)

		// Validate TokenResponse schema
		var tokenResponse map[string]interface{}
		body, err := io.ReadAll(resp.Body)
		require.NoError(t, err)

		err = json.Unmarshal(body, &tokenResponse)
		require.NoError(t, err)

		// Validate required fields from OpenAPI schema
		assert.Contains(t, tokenResponse, "access_token")
		assert.Contains(t, tokenResponse, "token_type")
		assert.Contains(t, tokenResponse, "expires_in")
		assert.Contains(t, tokenResponse, "scope")

		// Validate field types
		assert.IsType(t, "", tokenResponse["access_token"])
		assert.IsType(t, "", tokenResponse["token_type"])
		assert.IsType(t, float64(0), tokenResponse["expires_in"])
		assert.IsType(t, "", tokenResponse["scope"])

		// Validate enum values
		assert.Equal(t, "Bearer", tokenResponse["token_type"])
	})

	t.Run("Credential Request/Response Schema", func(t *testing.T) {
		// First get a valid token
		token := getValidToken(t, server.URL)

		// Test CredentialRequest schema compliance
		credentialRequest := map[string]interface{}{
			"format": "jwt_vc",
			"types":  []string{"VerifiableCredential", "IdentityCredential"},
		}

		reqBody, err := json.Marshal(credentialRequest)
		require.NoError(t, err)

		req, err := http.NewRequest("POST", server.URL+"/credential", bytes.NewBuffer(reqBody))
		require.NoError(t, err)

		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+token)

		client := &http.Client{Timeout: 10 * time.Second}
		resp, err := client.Do(req)
		require.NoError(t, err)
		defer resp.Body.Close()

		assert.Equal(t, http.StatusOK, resp.Status)

		// Validate CredentialResponse schema
		var credentialResponse map[string]interface{}
		body, err := io.ReadAll(resp.Body)
		require.NoError(t, err)

		err = json.Unmarshal(body, &credentialResponse)
		require.NoError(t, err)

		// Validate required fields
		assert.Contains(t, credentialResponse, "credential")
		assert.Contains(t, credentialResponse, "format")

		// Validate VerifiableCredential schema
		credential, ok := credentialResponse["credential"].(map[string]interface{})
		require.True(t, ok)

		validateVerifiableCredential(t, credential)
	})

	t.Run("Error Response Schema", func(t *testing.T) {
		// Test error response format
		resp, err := http.Post(server.URL+"/oauth/token", "application/json", bytes.NewBuffer([]byte(`{}`)))
		require.NoError(t, err)
		defer resp.Body.Close()

		assert.Equal(t, http.StatusBadRequest, resp.Status)

		var errorResponse map[string]interface{}
		body, err := io.ReadAll(resp.Body)
		require.NoError(t, err)

		err = json.Unmarshal(body, &errorResponse)
		require.NoError(t, err)

		// Validate Error schema
		assert.Contains(t, errorResponse, "error")
		assert.Contains(t, errorResponse, "message")
		assert.IsType(t, "", errorResponse["error"])
		assert.IsType(t, "", errorResponse["message"])
	})
}

func validateVerifiableCredential(t *testing.T, credential map[string]interface{}) {
	// Required fields from OpenAPI schema
	requiredFields := []string{
		"id", "@context", "type", "issuer", "issuanceDate", "credentialSubject",
	}

	for _, field := range requiredFields {
		assert.Contains(t, credential, field, "Missing required field: %s", field)
	}

	// Validate field types and formats
	assert.IsType(t, "", credential["id"])
	assert.IsType(t, []interface{}{}, credential["@context"])
	assert.IsType(t, []interface{}{}, credential["type"])
	assert.IsType(t, "", credential["issuer"])
	assert.IsType(t, "", credential["issuanceDate"])
	assert.IsType(t, map[string]interface{}{}, credential["credentialSubject"])

	// Validate ID format (should be UUID URN)
	id, _ := credential["id"].(string)
	assert.Regexp(t, `^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`, id)

	// Validate issuer format (should be DID)
	issuer, _ := credential["issuer"].(string)
	assert.Regexp(t, `^did:`, issuer)

	// Validate date format (should be RFC3339)
	issuanceDate, _ := credential["issuanceDate"].(string)
	_, err := time.Parse(time.RFC3339, issuanceDate)
	assert.NoError(t, err, "issuanceDate should be RFC3339 format")

	// Validate credentialSubject has required id field
	credentialSubject, _ := credential["credentialSubject"].(map[string]interface{})
	assert.Contains(t, credentialSubject, "id")
}

func getValidToken(t *testing.T, baseURL string) string {
	tokenRequest := map[string]interface{}{
		"grant_type": "client_credentials",
		"client_id":  "test-client",
		"scope":      "credential_issuance",
	}

	reqBody, err := json.Marshal(tokenRequest)
	require.NoError(t, err)

	resp, err := http.Post(baseURL+"/oauth/token", "application/json", bytes.NewBuffer(reqBody))
	require.NoError(t, err)
	defer resp.Body.Close()

	require.Equal(t, http.StatusOK, resp.Status)

	var tokenResponse map[string]interface{}
	body, err := io.ReadAll(resp.Body)
	require.NoError(t, err)

	err = json.Unmarshal(body, &tokenResponse)
	require.NoError(t, err)

	token, ok := tokenResponse["access_token"].(string)
	require.True(t, ok)

	return token
}

// startTestServer starts a test instance of the issuance gateway
func startTestServer(t *testing.T) *http.Server {
	// TODO: Implement test server startup
	// This should start your issuance gateway service in test mode
	// For now, return nil - you'll need to implement this based on your service setup

	// Example implementation:
	// server := httptest.NewServer(your_handler)
	// return server

	t.Skip("Test server implementation needed")
	return nil
}
