package main

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/cachet-id/cachet/services/common"
)

func testServerWithURLs(registryURL, issuanceURL, relayURL, verifierURL string) *Server {
	cfg := AdminConfig{
		Common: common.ServerConfig{
			Name:    "admin",
			Version: "0.1.0",
			Port:    "8091",
		},
		APIKey:      testAPIKey,
		RegistryURL: registryURL,
		IssuanceURL: issuanceURL,
		RelayURL:    relayURL,
		VerifierURL: verifierURL,
	}
	return NewServer(cfg)
}

func testServerFull(registryURL string) *Server {
	return testServer(registryURL)
}

func validSessionCookie(srv *Server) *http.Cookie {
	// Perform a login and extract the cookie.
	form := url.Values{"api_key": {testAPIKey}}
	req := httptest.NewRequest(http.MethodPost, "/login", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)
	for _, c := range rec.Result().Cookies() {
		if c.Name == sessionCookieName {
			return c
		}
	}
	return nil
}

func TestLoginPage_RendersForm(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	req := httptest.NewRequest(http.MethodGet, "/login", nil)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "api_key")
	assert.Contains(t, rec.Body.String(), "Operator Console")
}

func TestLoginSubmit_ValidKey_SetsCookieAndRedirects(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	form := url.Values{"api_key": {testAPIKey}}
	req := httptest.NewRequest(http.MethodPost, "/login", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Equal(t, "/", rec.Header().Get("Location"))

	var found bool
	for _, c := range rec.Result().Cookies() {
		if c.Name == sessionCookieName {
			found = true
			assert.True(t, c.HttpOnly)
		}
	}
	require.True(t, found, "session cookie must be set")
}

func TestLoginSubmit_InvalidKey_ShowsError(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	form := url.Values{"api_key": {"wrong-key"}}
	req := httptest.NewRequest(http.MethodPost, "/login", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "Invalid API key")
}

func TestProtectedRoute_NoCookie_RedirectsToLogin(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Equal(t, "/login", rec.Header().Get("Location"))
}

func TestProtectedRoute_ValidCookie_Returns200(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie, "must get a valid session cookie")

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "Dashboard")
}

func TestLogout_ClearsCookie(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodPost, "/logout", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Equal(t, "/login", rec.Header().Get("Location"))

	// Cookie should be cleared.
	for _, c := range rec.Result().Cookies() {
		if c.Name == sessionCookieName {
			assert.True(t, c.MaxAge < 0, "cookie must be expired")
		}
	}
}

func TestLoginPage_AlreadyLoggedIn_RedirectsToDashboard(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/login", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Equal(t, "/", rec.Header().Get("Location"))
}

func TestDashboard_AllServicesUp(t *testing.T) {
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode([]map[string]string{
			{"id": "pack.childcare"}, {"id": "pack.seller"},
		})
	}))
	defer registry.Close()

	relay := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"active": 5})
	}))
	defer relay.Close()

	verifier := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"active": 3})
	}))
	defer verifier.Close()

	srv := testServerWithURLs(registry.URL, "http://localhost:8090", relay.URL, verifier.URL)
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "Dashboard")
	assert.Contains(t, body, "2") // pack count
	assert.Contains(t, body, "5") // relay sessions
	assert.Contains(t, body, "3") // verifier sessions
}

func TestDashboard_ServiceDown_GracefulDegradation(t *testing.T) {
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode([]map[string]string{{"id": "pack.test"}})
	}))
	defer registry.Close()

	// relay and verifier are unreachable
	srv := testServerWithURLs(registry.URL, "http://127.0.0.1:1", "http://127.0.0.1:1", "http://127.0.0.1:1")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "Dashboard")
	assert.Contains(t, body, "Unavailable")
	assert.Contains(t, body, "1") // pack count still works
}

func TestPacksPage_RendersList(t *testing.T) {
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode([]map[string]interface{}{
			{"id": "pack.childcare", "name": "Childcare Readiness", "version": "1.0", "enabled": true},
			{"id": "pack.seller", "name": "Safe Seller", "version": "2.0", "enabled": false},
		})
	}))
	defer registry.Close()

	srv := testServerWithURLs(registry.URL, "http://localhost:8090", "http://localhost:8084", "http://localhost:8081")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/packs", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "pack.childcare")
	assert.Contains(t, body, "Childcare Readiness")
	assert.Contains(t, body, "pack.seller")
	assert.Contains(t, body, "Enabled")
	assert.Contains(t, body, "Disabled")
}

func TestPacksPage_EmptyList(t *testing.T) {
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode([]map[string]interface{}{})
	}))
	defer registry.Close()

	srv := testServerWithURLs(registry.URL, "http://localhost:8090", "http://localhost:8084", "http://localhost:8081")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/packs", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "No packs registered")
}

func TestTogglePack_Enable(t *testing.T) {
	var gotMethod, gotPath, gotBody string
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotMethod = r.Method
		gotPath = r.URL.Path
		b, _ := io.ReadAll(r.Body)
		gotBody = string(b)
		w.WriteHeader(http.StatusOK)
	}))
	defer registry.Close()

	srv := testServerWithURLs(registry.URL, "http://localhost:8090", "http://localhost:8084", "http://localhost:8081")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	form := url.Values{"enabled": {"true"}}
	req := httptest.NewRequest(http.MethodPost, "/packs/pack.test/toggle", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Contains(t, rec.Header().Get("Location"), "/packs")
	assert.Equal(t, http.MethodPatch, gotMethod)
	assert.Equal(t, "/internal/packs/pack.test/status", gotPath)
	assert.Contains(t, gotBody, `"enabled":true`)
}

func TestTogglePack_Disable(t *testing.T) {
	var gotBody string
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		gotBody = string(b)
		w.WriteHeader(http.StatusOK)
	}))
	defer registry.Close()

	srv := testServerWithURLs(registry.URL, "http://localhost:8090", "http://localhost:8084", "http://localhost:8081")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	form := url.Values{"enabled": {"false"}}
	req := httptest.NewRequest(http.MethodPost, "/packs/pack.test/toggle", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Contains(t, gotBody, `"enabled":false`)
}

func TestCreatePackPage_RendersForm(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/packs/new", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "New Pack")
	assert.Contains(t, body, `action="/packs/new"`)
}

func TestCreatePackSubmit_Valid(t *testing.T) {
	var gotBody string
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		gotBody = string(b)
		w.WriteHeader(http.StatusCreated)
	}))
	defer registry.Close()

	srv := testServerWithURLs(registry.URL, "http://localhost:8090", "http://localhost:8084", "http://localhost:8081")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	form := url.Values{
		"id": {"pack.test"}, "name": {"Test Pack"}, "version": {"1.0"},
		"purpose": {"Testing"}, "jurisdictions": {"GLOBAL"},
		"badge_label": {"Test"}, "badge_ttl": {"P30D"},
		"pred_id": {"age.ge.18"}, "pred_claim": {"age"},
		"pred_operator": {">="}, "pred_value": {"18"},
		"pred_proof_type": {"sd-jwt"}, "pred_issuers": {"did:veriff:*"},
	}
	req := httptest.NewRequest(http.MethodPost, "/packs/new", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Contains(t, rec.Header().Get("Location"), "/packs")
	assert.Contains(t, gotBody, `"pack.test"`)
	assert.Contains(t, gotBody, `"age.ge.18"`)
}

func TestCreatePackSubmit_MissingFields(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	form := url.Values{"id": {"pack.test"}} // missing name, version, predicates
	req := httptest.NewRequest(http.MethodPost, "/packs/new", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "required")
}

func TestEditPackPage_RendersFilledForm(t *testing.T) {
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"id": "pack.test", "name": "Test", "version": "1.0", "purpose": "Testing",
			"jurisdictions": []string{"GLOBAL"},
			"badge":         map[string]string{"label": "Test", "ttl": "P30D"},
			"predicates": []map[string]interface{}{
				{"id": "age.ge.18", "claim": "age", "operator": ">=", "value": 18, "proofType": "sd-jwt", "issuersAccepted": []string{"did:veriff:*"}},
			},
		})
	}))
	defer registry.Close()

	srv := testServerWithURLs(registry.URL, "http://localhost:8090", "http://localhost:8084", "http://localhost:8081")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/packs/pack.test/edit", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "Edit Pack")
	assert.Contains(t, body, "pack.test")
	assert.Contains(t, body, "age.ge.18")
}

func TestEditPackSubmit_Valid(t *testing.T) {
	var gotMethod string
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotMethod = r.Method
		w.WriteHeader(http.StatusOK)
	}))
	defer registry.Close()

	srv := testServerWithURLs(registry.URL, "http://localhost:8090", "http://localhost:8084", "http://localhost:8081")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	form := url.Values{
		"id": {"pack.test"}, "name": {"Test Pack"}, "version": {"2.0"},
		"pred_id": {"age.ge.21"}, "pred_claim": {"age"},
		"pred_operator": {">="}, "pred_value": {"21"},
	}
	req := httptest.NewRequest(http.MethodPost, "/packs/pack.test/edit", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Equal(t, http.MethodPut, gotMethod)
	assert.Contains(t, rec.Header().Get("Location"), "/packs")
}

func TestRevocationPage_ShowsStatusListInfo(t *testing.T) {
	issuance := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"id": "1", "purpose": "revocation", "allocated": 150, "revoked": 3, "capacity": 131072,
		})
	}))
	defer issuance.Close()

	srv := testServerWithURLs("http://localhost:8082", issuance.URL, "http://localhost:8084", "http://localhost:8081")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/revocation", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "Revocation")
	assert.Contains(t, body, "150") // allocated
	assert.Contains(t, body, "3")   // revoked
}

func TestRevokeSubmit_Valid(t *testing.T) {
	var gotPath string
	issuance := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		w.WriteHeader(http.StatusOK)
	}))
	defer issuance.Close()

	srv := testServerWithURLs("http://localhost:8082", issuance.URL, "http://localhost:8084", "http://localhost:8081")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	form := url.Values{"index": {"42"}, "list_id": {"1"}}
	req := httptest.NewRequest(http.MethodPost, "/revocation/revoke", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Contains(t, rec.Header().Get("Location"), "/revocation")
	assert.Contains(t, rec.Header().Get("Location"), "flash=")
	assert.Equal(t, "/status/1/revoke", gotPath)
}

func TestRevokeSubmit_InvalidIndex(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	form := url.Values{"index": {"abc"}}
	req := httptest.NewRequest(http.MethodPost, "/revocation/revoke", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Contains(t, rec.Header().Get("Location"), "flash_err=")
}

func TestSessionsPage_RendersBothServices(t *testing.T) {
	relay := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"sess-abc": 120})
	}))
	defer relay.Close()

	verifier := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"sess-xyz": 45})
	}))
	defer verifier.Close()

	srv := testServerWithURLs("http://localhost:8082", "http://localhost:8090", relay.URL, verifier.URL)
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/sessions", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "Relay Sessions")
	assert.Contains(t, body, "Verifier Sessions")
	assert.Contains(t, body, "sess-abc")
	assert.Contains(t, body, "sess-xyz")
}

func TestSessionsPage_OneServiceDown(t *testing.T) {
	relay := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"sess-abc": 120})
	}))
	defer relay.Close()

	// verifier is unreachable
	srv := testServerWithURLs("http://localhost:8082", "http://localhost:8090", relay.URL, "http://127.0.0.1:1")
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodGet, "/sessions", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "sess-abc")
	assert.Contains(t, body, "Unavailable")
}

func TestForceExpireWeb_Valid(t *testing.T) {
	var gotMethod, gotPath string
	verifier := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotMethod = r.Method
		gotPath = r.URL.Path
		w.WriteHeader(http.StatusNoContent)
	}))
	defer verifier.Close()

	srv := testServerWithURLs("http://localhost:8082", "http://localhost:8090", "http://localhost:8084", verifier.URL)
	cookie := validSessionCookie(srv)
	require.NotNil(t, cookie)

	req := httptest.NewRequest(http.MethodPost, "/sessions/verifier/sess-123/expire", nil)
	req.AddCookie(cookie)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusFound, rec.Code)
	assert.Contains(t, rec.Header().Get("Location"), "/sessions")
	assert.Equal(t, http.MethodDelete, gotMethod)
	assert.Equal(t, "/internal/sessions/sess-123", gotPath)
}

func TestNotFoundPage_ReturnsHTML(t *testing.T) {
	srv := testServerFull("http://localhost:8082")
	req := httptest.NewRequest(http.MethodGet, "/nonexistent", nil)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusNotFound, rec.Code)
	assert.Contains(t, rec.Header().Get("Content-Type"), "text/html")
	assert.Contains(t, rec.Body.String(), "not found")
}
