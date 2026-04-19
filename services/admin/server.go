package main

import (
	"io"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
)

// AdminConfig holds injectable dependencies for the admin service.
type AdminConfig struct {
	Common      common.ServerConfig
	APIKey      string
	RegistryURL string
	IssuanceURL string
	RelayURL    string
	VerifierURL string
}

// Server is the admin backoffice API and web UI.
type Server struct {
	router       *chi.Mux
	templates    templateMap
	apiKey       string
	cookieSecret []byte
	registryURL  string
	issuanceURL  string
	relayURL     string
	verifierURL  string
	httpClient   *http.Client
	startedAt    time.Time
}

// NewServer creates an admin server with API key authentication and web UI.
func NewServer(cfg AdminConfig) *Server {
	s := &Server{
		router:       common.NewRouter(cfg.Common),
		templates:    initTemplates(),
		apiKey:       cfg.APIKey,
		cookieSecret: deriveCookieSecret(cfg.APIKey),
		registryURL:  cfg.RegistryURL,
		issuanceURL:  cfg.IssuanceURL,
		relayURL:     cfg.RelayURL,
		verifierURL:  cfg.VerifierURL,
		httpClient:   &http.Client{Timeout: 10 * time.Second},
		startedAt:    time.Now(),
	}

	// Web UI routes (cookie auth).
	s.router.Get("/login", s.handleLoginPage)
	s.router.Post("/login", s.handleLoginSubmit)
	s.router.Post("/logout", s.handleLogout)

	s.router.Group(func(r chi.Router) {
		r.Use(s.CookieAuth)
		r.Get("/", s.handleDashboard)
		r.Get("/packs", s.handlePacksPage)
		r.Post("/packs/{id}/toggle", s.handleTogglePackStatus)
		r.Get("/packs/new", s.handleCreatePackPage)
		r.Post("/packs/new", s.handleCreatePackSubmit)
		r.Get("/packs/{id}/edit", s.handleEditPackPage)
		r.Post("/packs/{id}/edit", s.handleEditPackSubmit)
		r.Get("/revocation", s.handleRevocationPage)
		r.Post("/revocation/revoke", s.handleRevokeSubmit)
		r.Get("/sessions", s.handleSessionsPage)
		r.Post("/sessions/{service}/{id}/expire", s.handleForceExpireWeb)
	})

	// API routes (API key auth).
	s.router.Route("/admin", func(r chi.Router) {
		r.Use(APIKeyAuth(cfg.APIKey))
		r.Use(common.AuditMiddleware)

		r.Get("/status", s.handleStatus)
		r.Get("/packs", s.handleListPacks)
		r.Get("/packs/{id}", s.handleGetPack)
		r.Post("/packs", s.handleCreatePack)
		r.Put("/packs/{id}", s.handleUpdatePack)
		r.Patch("/packs/{id}/status", s.handlePatchPackStatus)

		r.Post("/credentials/{index}/revoke", s.handleRevokeCredential)
		r.Get("/statuslist/{id}", s.handleGetStatusListInfo)

		r.Get("/sessions", s.handleListSessions)
		r.Delete("/sessions/{service}/{id}", s.handleForceExpireSession)
	})

	s.router.NotFound(s.handleNotFound)

	return s
}

func (s *Server) Router() *chi.Mux { return s.router }

// handleStatus returns service info.
func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	common.WriteJSON(w, r, http.StatusOK, map[string]interface{}{
		"service":        "admin",
		"version":        "0.1.0",
		"uptime_seconds": int(time.Since(s.startedAt).Seconds()),
	})
}

// handleListPacks proxies to the registry's pack listing.
func (s *Server) handleListPacks(w http.ResponseWriter, r *http.Request) {
	resp, err := s.proxyGet(r, s.registryURL+"/registry/packs")
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to reach registry")
		common.WriteError(w, r, http.StatusBadGateway, "registry_error", "Failed to reach registry")
		return
	}
	defer func() { _ = resp.Body.Close() }()
	proxyResponse(w, resp)
}

// handleGetPack proxies a single pack lookup to the registry.
func (s *Server) handleGetPack(w http.ResponseWriter, r *http.Request) {
	packID := chi.URLParam(r, "id")
	resp, err := s.proxyGet(r, s.registryURL+"/registry/packs/"+packID)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to reach registry")
		common.WriteError(w, r, http.StatusBadGateway, "registry_error", "Failed to reach registry")
		return
	}
	defer func() { _ = resp.Body.Close() }()
	proxyResponse(w, resp)
}

// proxyGet makes a GET request to an internal service, forwarding the request context.
func (s *Server) proxyGet(r *http.Request, url string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	return s.httpClient.Do(req)
}

// proxyResponse copies an upstream response (status + body) to the client.
func proxyResponse(w http.ResponseWriter, resp *http.Response) {
	w.Header().Set("Content-Type", resp.Header.Get("Content-Type"))
	w.WriteHeader(resp.StatusCode)
	_, _ = io.Copy(w, resp.Body)
}
