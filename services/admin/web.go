package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"html/template"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
)

const (
	sessionCookieName = "cachet_admin_session"
	sessionMaxAge     = 8 * 60 * 60 // 8 hours
)

// PageData is the common data passed to all templates.
type PageData struct {
	Title     string
	HideNav   bool
	ActiveNav string
	Flash     string
	FlashErr  string
	Data      interface{}
}

// templateMap holds a pre-cloned layout+page template per page name.
type templateMap map[string]*template.Template

// initTemplates parses the layout then clones it for each page template.
func initTemplates() templateMap {
	funcMap := template.FuncMap{
		"formatDuration": formatDuration,
		"statusColor":    statusColor,
	}

	layout := template.Must(
		template.New("layout.html").Funcs(funcMap).ParseFS(templatesFS, "templates/layout.html"),
	)

	pages := []string{
		"templates/login.html",
		"templates/dashboard.html",
		"templates/packs.html",
		"templates/pack_form.html",
		"templates/revocation.html",
		"templates/sessions.html",
	}

	m := make(templateMap, len(pages))
	for _, page := range pages {
		t := template.Must(template.Must(layout.Clone()).ParseFS(templatesFS, page))
		m[page] = t
	}
	return m
}

func (s *Server) renderTemplate(w http.ResponseWriter, page string, data PageData) {
	t, ok := s.templates[page]
	if !ok {
		http.Error(w, "Template not found: "+page, http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := t.ExecuteTemplate(w, "layout", data); err != nil {
		http.Error(w, "Template error", http.StatusInternalServerError)
	}
}

// deriveCookieSecret produces a deterministic secret from the API key.
func deriveCookieSecret(apiKey string) []byte {
	mac := hmac.New(sha256.New, []byte(apiKey))
	mac.Write([]byte("cachet-admin-cookie"))
	return mac.Sum(nil)
}

func (s *Server) createSessionCookie(w http.ResponseWriter) {
	ts := fmt.Sprintf("%x", time.Now().Unix())
	sig := signTimestamp(ts, s.cookieSecret)
	value := base64.RawURLEncoding.EncodeToString([]byte(ts + "." + sig))
	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookieName,
		Value:    value,
		Path:     "/",
		MaxAge:   sessionMaxAge,
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
	})
}

func (s *Server) validateSessionCookie(r *http.Request) bool {
	cookie, err := r.Cookie(sessionCookieName)
	if err != nil {
		return false
	}
	raw, err := base64.RawURLEncoding.DecodeString(cookie.Value)
	if err != nil {
		return false
	}
	parts := strings.SplitN(string(raw), ".", 2)
	if len(parts) != 2 {
		return false
	}
	tsHex, sig := parts[0], parts[1]

	// Verify HMAC.
	expected := signTimestamp(tsHex, s.cookieSecret)
	if !hmac.Equal([]byte(sig), []byte(expected)) {
		return false
	}

	// Verify not expired.
	tsInt, err := hex.DecodeString(tsHex)
	if err != nil || len(tsInt) == 0 {
		return false
	}
	var ts int64
	for _, b := range tsInt {
		ts = ts<<8 | int64(b)
	}
	return time.Since(time.Unix(ts, 0)) < time.Duration(sessionMaxAge)*time.Second
}

func clearSessionCookie(w http.ResponseWriter) {
	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookieName,
		Value:    "",
		Path:     "/",
		MaxAge:   -1,
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
	})
}

func signTimestamp(tsHex string, secret []byte) string {
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(tsHex))
	return hex.EncodeToString(mac.Sum(nil))
}

// CookieAuth middleware redirects unauthenticated users to /login.
func (s *Server) CookieAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !s.validateSessionCookie(r) {
			http.Redirect(w, r, "/login", http.StatusFound)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// handleLoginPage renders the login form.
func (s *Server) handleLoginPage(w http.ResponseWriter, r *http.Request) {
	// If already logged in, redirect to dashboard.
	if s.validateSessionCookie(r) {
		http.Redirect(w, r, "/", http.StatusFound)
		return
	}
	s.renderTemplate(w, "templates/login.html", PageData{
		Title:   "Login",
		HideNav: true,
	})
}

// handleLoginSubmit validates the API key and sets a session cookie.
func (s *Server) handleLoginSubmit(w http.ResponseWriter, r *http.Request) {
	key := r.FormValue("api_key")
	if key != s.apiKey {
		s.renderTemplate(w, "templates/login.html", PageData{
			Title:   "Login",
			HideNav: true,
			Data:    "Invalid API key",
		})
		return
	}
	s.createSessionCookie(w)
	http.Redirect(w, r, "/", http.StatusFound)
}

// handleLogout clears the session cookie and redirects to login.
func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	clearSessionCookie(w)
	http.Redirect(w, r, "/login", http.StatusFound)
}

// flash helpers — pass messages via query params (stateless, no session store).

func flashFromQuery(r *http.Request) (string, string) {
	return r.URL.Query().Get("flash"), r.URL.Query().Get("flash_err")
}

func redirectWithFlash(w http.ResponseWriter, r *http.Request, path, flash string) {
	http.Redirect(w, r, path+"?flash="+flash, http.StatusFound)
}

func redirectWithFlashErr(w http.ResponseWriter, r *http.Request, path, flashErr string) {
	http.Redirect(w, r, path+"?flash_err="+flashErr, http.StatusFound)
}

// formatDuration converts seconds to a human-friendly string.
func formatDuration(seconds int) string {
	d := time.Duration(seconds) * time.Second
	if d < time.Minute {
		return fmt.Sprintf("%ds", seconds)
	}
	if d < time.Hour {
		return fmt.Sprintf("%dm %ds", int(d.Minutes()), int(d.Seconds())%60)
	}
	return fmt.Sprintf("%dh %dm", int(d.Hours()), int(d.Minutes())%60)
}

// statusColor returns a CSS color for a trust state.
func statusColor(status string) string {
	switch status {
	case "verified", "enabled":
		return "#10B981"
	case "pending":
		return "#F59E0B"
	case "revoked", "disabled":
		return "#F97068"
	default:
		return "#64748B"
	}
}

// handleNotFound renders a 404 page.
func (s *Server) handleNotFound(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusNotFound)
	s.renderTemplate(w, "templates/dashboard.html", PageData{
		Title:    "Not Found",
		FlashErr: "Page not found",
	})
}

// --- Web handlers ---

// DashboardData holds the data rendered on the dashboard page.
type DashboardData struct {
	Version          string
	Uptime           string
	PackCount        int
	RelaySessions    map[string]interface{}
	VerifierSessions map[string]interface{}
	Errors           []string
}

func (s *Server) handleDashboard(w http.ResponseWriter, r *http.Request) {
	flash, flashErr := flashFromQuery(r)
	data := DashboardData{
		Version: "0.1.0",
		Uptime:  formatDuration(int(time.Since(s.startedAt).Seconds())),
	}

	// Fetch pack count from registry.
	if packs, err := s.fetchJSONSlice(r, s.registryURL+"/registry/packs"); err == nil {
		data.PackCount = len(packs)
	} else {
		log.Ctx(r.Context()).Warn().Err(err).Msg("dashboard: failed to fetch packs")
		data.Errors = append(data.Errors, "Registry unavailable")
	}

	// Fetch relay sessions.
	if relay, err := s.fetchJSONMap(r, s.relayURL+"/internal/sessions"); err == nil {
		data.RelaySessions = relay
	} else {
		log.Ctx(r.Context()).Warn().Err(err).Msg("dashboard: failed to fetch relay sessions")
		data.Errors = append(data.Errors, "Relay unavailable")
	}

	// Fetch verifier sessions.
	if verifier, err := s.fetchJSONMap(r, s.verifierURL+"/internal/sessions"); err == nil {
		data.VerifierSessions = verifier
	} else {
		log.Ctx(r.Context()).Warn().Err(err).Msg("dashboard: failed to fetch verifier sessions")
		data.Errors = append(data.Errors, "Verifier unavailable")
	}

	s.renderTemplate(w, "templates/dashboard.html", PageData{
		Title:     "Dashboard",
		ActiveNav: "dashboard",
		Flash:     flash,
		FlashErr:  flashErr,
		Data:      data,
	})
}

// fetchJSONMap fetches a URL and decodes the response as a JSON object.
func (s *Server) fetchJSONMap(r *http.Request, url string) (map[string]interface{}, error) {
	resp, err := s.proxyGet(r, url)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	var result map[string]interface{}
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, err
	}
	return result, nil
}

// fetchJSONSlice fetches a URL and decodes the response as a JSON array.
func (s *Server) fetchJSONSlice(r *http.Request, url string) ([]interface{}, error) {
	resp, err := s.proxyGet(r, url)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	var result []interface{}
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, err
	}
	return result, nil
}

func (s *Server) handlePacksPage(w http.ResponseWriter, r *http.Request) {
	flash, flashErr := flashFromQuery(r)
	var packs []map[string]interface{}
	var errors []string

	if raw, err := s.fetchJSONSlice(r, s.registryURL+"/registry/packs"); err == nil {
		for _, item := range raw {
			if m, ok := item.(map[string]interface{}); ok {
				packs = append(packs, m)
			}
		}
	} else {
		log.Ctx(r.Context()).Warn().Err(err).Msg("packs page: failed to fetch packs")
		errors = append(errors, "Registry unavailable")
	}

	s.renderTemplate(w, "templates/packs.html", PageData{
		Title:     "Packs",
		ActiveNav: "packs",
		Flash:     flash,
		FlashErr:  flashErr,
		Data: map[string]interface{}{
			"Packs":  packs,
			"Errors": errors,
		},
	})
}

func (s *Server) handleTogglePackStatus(w http.ResponseWriter, r *http.Request) {
	packID := chi.URLParam(r, "id")
	enabled := r.FormValue("enabled")

	body := fmt.Sprintf(`{"enabled":%s}`, enabled)
	resp, err := s.proxyBody(r, http.MethodPatch, s.registryURL+"/internal/packs/"+packID+"/status", []byte(body))
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("toggle pack: failed to reach registry")
		redirectWithFlashErr(w, r, "/packs", "Registry+unavailable")
		return
	}
	_ = resp.Body.Close()

	action := "pack.disabled"
	label := "disabled"
	if enabled == "true" {
		action = "pack.enabled"
		label = "enabled"
	}
	common.AuditLog(r, action, packID, "success")
	redirectWithFlash(w, r, "/packs", "Pack+"+packID+"+"+label)
}

// PackFormData holds data for the pack create/edit form template.
type PackFormData struct {
	Mode   string // "create" or "edit"
	Action string
	Error  string
	Pack   PackFormValues
}

// PackFormValues are the pre-fill values for the pack form.
type PackFormValues struct {
	ID            string
	Name          string
	Version       string
	Purpose       string
	Jurisdictions string
	BadgeLabel    string
	BadgeTTL      string
	Predicates    []PredicateFormValues
}

// PredicateFormValues holds one predicate row.
type PredicateFormValues struct {
	ID        string
	Claim     string
	Operator  string
	Value     string
	ProofType string
	Issuers   string
}

func (s *Server) handleCreatePackPage(w http.ResponseWriter, r *http.Request) {
	s.renderTemplate(w, "templates/pack_form.html", PageData{
		Title:     "New Pack",
		ActiveNav: "packs",
		Data: PackFormData{
			Mode:   "create",
			Action: "/packs/new",
			Pack:   PackFormValues{Predicates: []PredicateFormValues{{}}},
		},
	})
}

func (s *Server) handleCreatePackSubmit(w http.ResponseWriter, r *http.Request) {
	packJSON, formVals, err := buildPackJSON(r)
	if err != nil {
		s.renderTemplate(w, "templates/pack_form.html", PageData{
			Title: "New Pack", ActiveNav: "packs",
			Data: PackFormData{Mode: "create", Action: "/packs/new", Error: err.Error(), Pack: formVals},
		})
		return
	}

	resp, err := s.proxyBody(r, http.MethodPost, s.registryURL+"/internal/packs", packJSON)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("create pack: failed to reach registry")
		s.renderTemplate(w, "templates/pack_form.html", PageData{
			Title: "New Pack", ActiveNav: "packs",
			Data: PackFormData{Mode: "create", Action: "/packs/new", Error: "Registry unavailable", Pack: formVals},
		})
		return
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusCreated && resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		s.renderTemplate(w, "templates/pack_form.html", PageData{
			Title: "New Pack", ActiveNav: "packs",
			Data: PackFormData{Mode: "create", Action: "/packs/new", Error: "Registry error: " + string(body), Pack: formVals},
		})
		return
	}

	common.AuditLog(r, "pack.created", formVals.ID, "success")
	redirectWithFlash(w, r, "/packs", "Pack+created")
}

func (s *Server) handleEditPackPage(w http.ResponseWriter, r *http.Request) {
	packID := chi.URLParam(r, "id")
	packMap, err := s.fetchJSONMap(r, s.registryURL+"/registry/packs/"+packID)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("edit pack: failed to fetch pack")
		redirectWithFlashErr(w, r, "/packs", "Failed+to+load+pack")
		return
	}

	formVals := mapToFormValues(packMap)
	s.renderTemplate(w, "templates/pack_form.html", PageData{
		Title:     "Edit Pack",
		ActiveNav: "packs",
		Data: PackFormData{
			Mode:   "edit",
			Action: "/packs/" + packID + "/edit",
			Pack:   formVals,
		},
	})
}

func (s *Server) handleEditPackSubmit(w http.ResponseWriter, r *http.Request) {
	packID := chi.URLParam(r, "id")
	packJSON, formVals, err := buildPackJSON(r)
	if err != nil {
		s.renderTemplate(w, "templates/pack_form.html", PageData{
			Title: "Edit Pack", ActiveNav: "packs",
			Data: PackFormData{Mode: "edit", Action: "/packs/" + packID + "/edit", Error: err.Error(), Pack: formVals},
		})
		return
	}

	resp, err := s.proxyBody(r, http.MethodPut, s.registryURL+"/internal/packs/"+packID, packJSON)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("edit pack: failed to reach registry")
		s.renderTemplate(w, "templates/pack_form.html", PageData{
			Title: "Edit Pack", ActiveNav: "packs",
			Data: PackFormData{Mode: "edit", Action: "/packs/" + packID + "/edit", Error: "Registry unavailable", Pack: formVals},
		})
		return
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		s.renderTemplate(w, "templates/pack_form.html", PageData{
			Title: "Edit Pack", ActiveNav: "packs",
			Data: PackFormData{Mode: "edit", Action: "/packs/" + packID + "/edit", Error: "Registry error: " + string(body), Pack: formVals},
		})
		return
	}

	common.AuditLog(r, "pack.updated", packID, "success")
	redirectWithFlash(w, r, "/packs", "Pack+updated")
}

// buildPackJSON parses form values into a pack definition JSON.
func buildPackJSON(r *http.Request) ([]byte, PackFormValues, error) {
	if err := r.ParseForm(); err != nil {
		return nil, PackFormValues{}, fmt.Errorf("invalid form data")
	}

	fv := PackFormValues{
		ID:            r.FormValue("id"),
		Name:          r.FormValue("name"),
		Version:       r.FormValue("version"),
		Purpose:       r.FormValue("purpose"),
		Jurisdictions: r.FormValue("jurisdictions"),
		BadgeLabel:    r.FormValue("badge_label"),
		BadgeTTL:      r.FormValue("badge_ttl"),
	}

	if fv.ID == "" || fv.Name == "" || fv.Version == "" {
		return nil, fv, fmt.Errorf("id, name, and version are required")
	}

	// Parse predicates from parallel arrays.
	predIDs := r.Form["pred_id"]
	predClaims := r.Form["pred_claim"]
	predOps := r.Form["pred_operator"]
	predVals := r.Form["pred_value"]
	predProofs := r.Form["pred_proof_type"]
	predIssuers := r.Form["pred_issuers"]

	var predicates []map[string]interface{}
	for i := range predIDs {
		if predIDs[i] == "" {
			continue
		}
		p := map[string]interface{}{
			"id":       predIDs[i],
			"claim":    safeIndex(predClaims, i),
			"operator": safeIndex(predOps, i),
		}

		// Try to parse value as number or bool; fall back to string.
		valStr := safeIndex(predVals, i)
		p["value"] = parseValue(valStr)

		if pt := safeIndex(predProofs, i); pt != "" {
			p["proofType"] = pt
		}
		if iss := safeIndex(predIssuers, i); iss != "" {
			p["issuersAccepted"] = splitCSV(iss)
		}

		predicates = append(predicates, p)

		fv.Predicates = append(fv.Predicates, PredicateFormValues{
			ID:        predIDs[i],
			Claim:     safeIndex(predClaims, i),
			Operator:  safeIndex(predOps, i),
			Value:     valStr,
			ProofType: safeIndex(predProofs, i),
			Issuers:   safeIndex(predIssuers, i),
		})
	}

	if len(predicates) == 0 {
		return nil, fv, fmt.Errorf("at least one predicate is required")
	}

	// Build jurisdictions array.
	jurisdictions := splitCSV(fv.Jurisdictions)
	if len(jurisdictions) == 0 {
		jurisdictions = []string{"GLOBAL"}
	}

	pack := map[string]interface{}{
		"id":            fv.ID,
		"name":          fv.Name,
		"version":       fv.Version,
		"purpose":       fv.Purpose,
		"jurisdictions": jurisdictions,
		"predicates":    predicates,
	}

	if fv.BadgeLabel != "" || fv.BadgeTTL != "" {
		badge := map[string]string{}
		if fv.BadgeLabel != "" {
			badge["label"] = fv.BadgeLabel
		}
		if fv.BadgeTTL != "" {
			badge["ttl"] = fv.BadgeTTL
		}
		pack["badge"] = badge
	}

	data, err := json.Marshal(pack)
	return data, fv, err
}

// mapToFormValues converts a JSON map (from registry) to form pre-fill values.
func mapToFormValues(m map[string]interface{}) PackFormValues {
	fv := PackFormValues{
		ID:      fmt.Sprintf("%v", m["id"]),
		Name:    fmt.Sprintf("%v", m["name"]),
		Version: fmt.Sprintf("%v", m["version"]),
		Purpose: fmt.Sprintf("%v", m["purpose"]),
	}

	if j, ok := m["jurisdictions"].([]interface{}); ok {
		var parts []string
		for _, v := range j {
			parts = append(parts, fmt.Sprintf("%v", v))
		}
		fv.Jurisdictions = strings.Join(parts, ", ")
	}

	if badge, ok := m["badge"].(map[string]interface{}); ok {
		fv.BadgeLabel = fmt.Sprintf("%v", badge["label"])
		fv.BadgeTTL = fmt.Sprintf("%v", badge["ttl"])
	}

	if preds, ok := m["predicates"].([]interface{}); ok {
		for _, raw := range preds {
			p, ok := raw.(map[string]interface{})
			if !ok {
				continue
			}
			var issuers string
			if iss, ok := p["issuersAccepted"].([]interface{}); ok {
				var parts []string
				for _, v := range iss {
					parts = append(parts, fmt.Sprintf("%v", v))
				}
				issuers = strings.Join(parts, ", ")
			}
			fv.Predicates = append(fv.Predicates, PredicateFormValues{
				ID:        fmt.Sprintf("%v", p["id"]),
				Claim:     fmt.Sprintf("%v", p["claim"]),
				Operator:  fmt.Sprintf("%v", p["operator"]),
				Value:     fmt.Sprintf("%v", p["value"]),
				ProofType: fmt.Sprintf("%v", p["proofType"]),
				Issuers:   issuers,
			})
		}
	}
	return fv
}

func safeIndex(s []string, i int) string {
	if i < len(s) {
		return s[i]
	}
	return ""
}

func splitCSV(s string) []string {
	if s == "" {
		return nil
	}
	parts := strings.Split(s, ",")
	var result []string
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			result = append(result, p)
		}
	}
	return result
}

func parseValue(s string) interface{} {
	if s == "true" {
		return true
	}
	if s == "false" {
		return false
	}
	var n json.Number
	if err := json.Unmarshal([]byte(s), &n); err == nil {
		if i, err := n.Int64(); err == nil {
			return i
		}
		if f, err := n.Float64(); err == nil {
			return f
		}
	}
	return s
}

// RevocationData holds data for the revocation page.
type RevocationData struct {
	StatusList map[string]interface{}
	Error      string
}

func (s *Server) handleRevocationPage(w http.ResponseWriter, r *http.Request) {
	flash, flashErr := flashFromQuery(r)
	data := RevocationData{}

	if sl, err := s.fetchJSONMap(r, s.issuanceURL+"/status/1/info"); err == nil {
		data.StatusList = sl
	} else {
		log.Ctx(r.Context()).Warn().Err(err).Msg("revocation: failed to fetch status list")
		data.Error = "Status list unavailable"
	}

	s.renderTemplate(w, "templates/revocation.html", PageData{
		Title:     "Revocation",
		ActiveNav: "revocation",
		Flash:     flash,
		FlashErr:  flashErr,
		Data:      data,
	})
}

func (s *Server) handleRevokeSubmit(w http.ResponseWriter, r *http.Request) {
	indexStr := r.FormValue("index")
	listID := r.FormValue("list_id")
	if listID == "" {
		listID = "1"
	}

	index, err := strconv.Atoi(indexStr)
	if err != nil {
		redirectWithFlashErr(w, r, "/revocation", "Invalid+index")
		return
	}

	body := fmt.Sprintf(`{"index":%d}`, index)
	resp, err := s.proxyBody(r, http.MethodPost, s.issuanceURL+"/status/"+listID+"/revoke", []byte(body))
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("revoke: failed to reach issuance gateway")
		redirectWithFlashErr(w, r, "/revocation", "Issuance+gateway+unavailable")
		return
	}
	_ = resp.Body.Close()

	resource := fmt.Sprintf("list:%s/index:%d", listID, index)
	common.AuditLog(r, "credential.revoked", resource, "success")
	redirectWithFlash(w, r, "/revocation", fmt.Sprintf("Credential+at+index+%d+revoked", index))
}

// SessionsData holds data for the sessions page.
type SessionsData struct {
	Relay    interface{}
	Verifier interface{}
	Errors   []string
}

func (s *Server) handleSessionsPage(w http.ResponseWriter, r *http.Request) {
	flash, flashErr := flashFromQuery(r)
	data := SessionsData{}

	if relay, err := s.fetchSessionStats(r, s.relayURL+"/internal/sessions"); err == nil {
		data.Relay = relay
	} else {
		log.Ctx(r.Context()).Warn().Err(err).Msg("sessions: failed to fetch relay sessions")
		data.Errors = append(data.Errors, "Relay unavailable")
	}

	if verifier, err := s.fetchSessionStats(r, s.verifierURL+"/internal/sessions"); err == nil {
		data.Verifier = verifier
	} else {
		log.Ctx(r.Context()).Warn().Err(err).Msg("sessions: failed to fetch verifier sessions")
		data.Errors = append(data.Errors, "Verifier unavailable")
	}

	s.renderTemplate(w, "templates/sessions.html", PageData{
		Title:     "Sessions",
		ActiveNav: "sessions",
		Flash:     flash,
		FlashErr:  flashErr,
		Data:      data,
	})
}

func (s *Server) handleForceExpireWeb(w http.ResponseWriter, r *http.Request) {
	service := chi.URLParam(r, "service")
	sessionID := chi.URLParam(r, "id")

	var baseURL string
	switch service {
	case "relay":
		baseURL = s.relayURL
	case "verifier":
		baseURL = s.verifierURL
	default:
		redirectWithFlashErr(w, r, "/sessions", "Invalid+service")
		return
	}

	req, err := http.NewRequestWithContext(r.Context(), http.MethodDelete, baseURL+"/internal/sessions/"+sessionID, nil)
	if err != nil {
		redirectWithFlashErr(w, r, "/sessions", "Request+failed")
		return
	}
	resp, err := s.httpClient.Do(req)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("force expire: failed to reach service")
		redirectWithFlashErr(w, r, "/sessions", service+"+unavailable")
		return
	}
	_ = resp.Body.Close()

	common.AuditLog(r, "session.force_expired", service+":"+sessionID, "success")
	redirectWithFlash(w, r, "/sessions", "Session+"+sessionID+"+expired")
}
