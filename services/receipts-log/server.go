package main

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
)

type Server struct {
	router *chi.Mux
}

func NewServer(cfg common.ServerConfig) *Server {
	s := &Server{router: common.NewRouter(cfg)}
	s.router.Post("/receipts/hash", s.handleSubmitHash)
	s.router.Get("/log/sth", s.handleSignedTreeHead)
	s.router.Get("/log/proof", s.handleInclusionProof)
	return s
}

func (s *Server) Router() *chi.Mux { return s.router }

func (s *Server) handleSubmitHash(w http.ResponseWriter, r *http.Request) {
	var req models.ReceiptHashRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}
	accepted := true
	anchored := false
	resp := models.ReceiptHashResponse{Accepted: &accepted, Hash: &req.ReceiptHash, Anchored: &anchored}
	common.WriteJSON(w, r, http.StatusOK, resp)
}

func (s *Server) handleSignedTreeHead(w http.ResponseWriter, r *http.Request) {
	treeSize := 0
	rootHash := ""
	ts := time.Date(2025, 8, 31, 11, 41, 30, 0, time.UTC)
	resp := models.SignedTreeHead{TreeSize: &treeSize, RootHash: &rootHash, Timestamp: &ts}
	common.WriteJSON(w, r, http.StatusOK, resp)
}

func (s *Server) handleInclusionProof(w http.ResponseWriter, r *http.Request) {
	included := false
	resp := models.InclusionProof{Included: &included}
	common.WriteJSON(w, r, http.StatusOK, resp)
}
