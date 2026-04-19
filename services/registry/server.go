package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/registry/internal/pack"
)

const policyManifest = `id: policy.cachet.manifest
version: 0.1.0
issuedAt: 2025-08-31T00:00:00Z
signingDid: did:web:cachet.id#keys-1`

type Server struct {
	router    *chi.Mux
	packStore *pack.Store
}

func NewServer(cfg common.ServerConfig) *Server {
	return NewServerWithOverlay(cfg, "")
}

func NewServerWithOverlay(cfg common.ServerConfig, overlayDir string) *Server {
	var packStore *pack.Store
	var err error
	if overlayDir != "" {
		packStore, err = pack.LoadWithOverlay(pack.EmbeddedPacksFS(), overlayDir)
	} else {
		packStore, err = pack.LoadFromFS(pack.EmbeddedPacksFS())
	}
	if err != nil {
		log.Fatal().Err(err).Msg("failed to load pack definitions")
	}

	cfg.ReadinessChecks = append(cfg.ReadinessChecks, func(_ context.Context) error {
		if len(packStore.List()) == 0 {
			return fmt.Errorf("no packs loaded")
		}
		return nil
	})

	s := &Server{
		router:    common.NewRouter(cfg),
		packStore: packStore,
	}
	s.router.Get("/policy/manifest", s.handlePolicyManifest)
	s.router.Get("/registry/packs", s.handleListPacks)
	s.router.Get("/registry/packs/{packId}", s.handleGetPack)

	// Internal endpoints for admin service
	s.router.Post("/internal/reload", s.handleReload)
	s.router.Post("/internal/packs", s.handleCreatePack)
	s.router.Put("/internal/packs/{packId}", s.handleUpdatePack)
	s.router.Patch("/internal/packs/{packId}/status", s.handlePatchPackStatus)

	return s
}

func (s *Server) Router() *chi.Mux { return s.router }

func (s *Server) handlePolicyManifest(w http.ResponseWriter, r *http.Request) {
	log.Ctx(r.Context()).Info().Msg("policy manifest requested")
	w.Header().Set("Content-Type", "text/yaml")
	if _, err := w.Write([]byte(policyManifest)); err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("write failed")
	}
}

func (s *Server) handleListPacks(w http.ResponseWriter, r *http.Request) {
	packs := s.packStore.List()
	log.Ctx(r.Context()).Info().Int("pack_count", len(packs)).Msg("listing pack definitions")
	common.WriteJSON(w, r, http.StatusOK, packs)
}

func (s *Server) handleGetPack(w http.ResponseWriter, r *http.Request) {
	packId := chi.URLParam(r, "packId")
	p, ok := s.packStore.Get(packId)
	if !ok {
		common.WriteError(w, r, http.StatusNotFound, "not_found", "Pack not found: "+packId)
		return
	}
	log.Ctx(r.Context()).Info().Str("pack_id", packId).Msg("pack definition requested")
	common.WriteJSON(w, r, http.StatusOK, p)
}

// handleReload triggers a hot-reload of pack definitions from embedded + overlay.
func (s *Server) handleReload(w http.ResponseWriter, r *http.Request) {
	if err := s.packStore.Reload(); err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("pack reload failed")
		common.WriteError(w, r, http.StatusInternalServerError, "reload_failed", err.Error())
		return
	}
	log.Ctx(r.Context()).Info().Int("pack_count", len(s.packStore.List())).Msg("packs reloaded")
	common.WriteJSON(w, r, http.StatusOK, map[string]string{"status": "reloaded"})
}

// handleCreatePack writes a new pack to the overlay directory.
func (s *Server) handleCreatePack(w http.ResponseWriter, r *http.Request) {
	var p models.PackDefinition
	if err := json.NewDecoder(r.Body).Decode(&p); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_json", "Invalid pack JSON")
		return
	}

	if p.Id == "" || p.Name == "" || p.Version == "" {
		common.WriteError(w, r, http.StatusBadRequest, "validation_error", "id, name, and version are required")
		return
	}
	if len(p.Predicates) == 0 {
		common.WriteError(w, r, http.StatusBadRequest, "validation_error", "predicates must be non-empty")
		return
	}

	// Check for conflict
	if _, exists := s.packStore.Get(p.Id); exists {
		common.WriteError(w, r, http.StatusConflict, "already_exists", "Pack already exists: "+p.Id)
		return
	}

	if err := s.packStore.Put(p); err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to write pack")
		common.WriteError(w, r, http.StatusInternalServerError, "write_failed", err.Error())
		return
	}

	log.Ctx(r.Context()).Info().Str("pack_id", p.Id).Msg("pack created")
	common.WriteJSON(w, r, http.StatusCreated, p)
}

// handleUpdatePack updates an existing pack in the overlay directory.
func (s *Server) handleUpdatePack(w http.ResponseWriter, r *http.Request) {
	packID := chi.URLParam(r, "packId")

	var p models.PackDefinition
	if err := json.NewDecoder(r.Body).Decode(&p); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_json", "Invalid pack JSON")
		return
	}

	if p.Id != packID {
		common.WriteError(w, r, http.StatusBadRequest, "id_mismatch", "Pack ID in body must match URL")
		return
	}

	if _, exists := s.packStore.Get(packID); !exists {
		common.WriteError(w, r, http.StatusNotFound, "not_found", "Pack not found: "+packID)
		return
	}

	if err := s.packStore.Put(p); err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to update pack")
		common.WriteError(w, r, http.StatusInternalServerError, "write_failed", err.Error())
		return
	}

	log.Ctx(r.Context()).Info().Str("pack_id", p.Id).Msg("pack updated")
	common.WriteJSON(w, r, http.StatusOK, p)
}

// handlePatchPackStatus enables or disables a pack.
func (s *Server) handlePatchPackStatus(w http.ResponseWriter, r *http.Request) {
	packID := chi.URLParam(r, "packId")

	var req struct {
		Enabled bool `json:"enabled"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_json", "Invalid JSON")
		return
	}

	if !s.packStore.SetEnabled(packID, req.Enabled) {
		common.WriteError(w, r, http.StatusNotFound, "not_found", "Pack not found: "+packID)
		return
	}

	log.Ctx(r.Context()).Info().Str("pack_id", packID).Bool("enabled", req.Enabled).Msg("pack status changed")
	common.WriteJSON(w, r, http.StatusOK, map[string]interface{}{
		"id":      packID,
		"enabled": req.Enabled,
	})
}
