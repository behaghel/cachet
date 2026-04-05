package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/cachet-id/cachet/services/common"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func testRelayServer() *Server {
	return NewServer(common.ServerConfig{Name: "test-relay", Version: "0.0.1", Port: "0"})
}

func TestHealthCheck(t *testing.T) {
	s := testRelayServer()
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/health", nil))
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestCreateSession(t *testing.T) {
	s := testRelayServer()
	reqBody := []byte(`{"signed":"request-object-jwt"}`)
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/sessions", bytes.NewReader(reqBody)))

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.NotEmpty(t, resp["sessionId"])
	assert.Contains(t, resp["requestUri"], resp["sessionId"])
	assert.Contains(t, resp["responseUri"], resp["sessionId"])
}

func TestGetRequest_Found(t *testing.T) {
	s := testRelayServer()
	payload := []byte(`test-request-object`)

	// Create session
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/sessions", bytes.NewReader(payload)))
	var created map[string]string
	json.Unmarshal(w.Body.Bytes(), &created)

	// Get request
	w2 := httptest.NewRecorder()
	s.Router().ServeHTTP(w2, httptest.NewRequest(http.MethodGet, "/sessions/"+created["sessionId"]+"/request", nil))

	assert.Equal(t, http.StatusOK, w2.Code)
	body, _ := io.ReadAll(w2.Body)
	assert.Equal(t, payload, body)
}

func TestGetRequest_NotFound(t *testing.T) {
	s := testRelayServer()
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/sessions/nonexistent/request", nil))
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestPostResponse(t *testing.T) {
	s := testRelayServer()
	// Create session
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/sessions", bytes.NewReader([]byte("req"))))
	var created map[string]string
	json.Unmarshal(w.Body.Bytes(), &created)
	sid := created["sessionId"]

	// Post response
	vpPayload := []byte("encrypted-vp-payload")
	w2 := httptest.NewRecorder()
	s.Router().ServeHTTP(w2, httptest.NewRequest(http.MethodPost, "/sessions/"+sid+"/response", bytes.NewReader(vpPayload)))
	assert.Equal(t, http.StatusNoContent, w2.Code)
}

func TestGetResponse_BeforeHolderPosts(t *testing.T) {
	s := testRelayServer()
	// Create session
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/sessions", bytes.NewReader([]byte("req"))))
	var created map[string]string
	json.Unmarshal(w.Body.Bytes(), &created)

	// Poll before holder responds — should get 204
	w2 := httptest.NewRecorder()
	s.Router().ServeHTTP(w2, httptest.NewRequest(http.MethodGet, "/sessions/"+created["sessionId"]+"/response", nil))
	assert.Equal(t, http.StatusNoContent, w2.Code)
}

func TestGetResponse_AfterHolderPosts(t *testing.T) {
	s := testRelayServer()
	// Create session
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/sessions", bytes.NewReader([]byte("req"))))
	var created map[string]string
	json.Unmarshal(w.Body.Bytes(), &created)
	sid := created["sessionId"]

	// Holder posts response
	vpPayload := []byte("encrypted-vp")
	w2 := httptest.NewRecorder()
	s.Router().ServeHTTP(w2, httptest.NewRequest(http.MethodPost, "/sessions/"+sid+"/response", bytes.NewReader(vpPayload)))
	require.Equal(t, http.StatusNoContent, w2.Code)

	// Verifier polls — should get 200 with payload
	w3 := httptest.NewRecorder()
	s.Router().ServeHTTP(w3, httptest.NewRequest(http.MethodGet, "/sessions/"+sid+"/response", nil))
	assert.Equal(t, http.StatusOK, w3.Code)
	body, _ := io.ReadAll(w3.Body)
	assert.Equal(t, vpPayload, body)
}

func TestGetResponse_NotFound(t *testing.T) {
	s := testRelayServer()
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/sessions/nonexistent/response", nil))
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestFullFlow(t *testing.T) {
	s := testRelayServer()
	requestObj := []byte(`{"alg":"ES256","typ":"oauth-authz-req+jwt"}`)
	encryptedVP := []byte(`JWE-encrypted-verifiable-presentation`)

	// 1. Verifier creates session
	w1 := httptest.NewRecorder()
	s.Router().ServeHTTP(w1, httptest.NewRequest(http.MethodPost, "/sessions", bytes.NewReader(requestObj)))
	require.Equal(t, http.StatusOK, w1.Code)
	var created map[string]string
	json.Unmarshal(w1.Body.Bytes(), &created)
	sid := created["sessionId"]

	// 2. Holder scans QR, fetches request
	w2 := httptest.NewRecorder()
	s.Router().ServeHTTP(w2, httptest.NewRequest(http.MethodGet, "/sessions/"+sid+"/request", nil))
	require.Equal(t, http.StatusOK, w2.Code)
	body2, _ := io.ReadAll(w2.Body)
	assert.Equal(t, requestObj, body2)

	// 3. Verifier polls — no response yet
	w3 := httptest.NewRecorder()
	s.Router().ServeHTTP(w3, httptest.NewRequest(http.MethodGet, "/sessions/"+sid+"/response", nil))
	assert.Equal(t, http.StatusNoContent, w3.Code)

	// 4. Holder posts encrypted VP
	w4 := httptest.NewRecorder()
	s.Router().ServeHTTP(w4, httptest.NewRequest(http.MethodPost, "/sessions/"+sid+"/response", bytes.NewReader(encryptedVP)))
	require.Equal(t, http.StatusNoContent, w4.Code)

	// 5. Verifier polls — gets the VP
	w5 := httptest.NewRecorder()
	s.Router().ServeHTTP(w5, httptest.NewRequest(http.MethodGet, "/sessions/"+sid+"/response", nil))
	require.Equal(t, http.StatusOK, w5.Code)
	body5, _ := io.ReadAll(w5.Body)
	assert.Equal(t, encryptedVP, body5)
}
