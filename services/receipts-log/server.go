package main

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/cachet-id/cachet/services/common"
)

type submitRequest struct {
	ReceiptHash string `json:"receiptHash"`
}

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
	var req submitRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}
	resp := map[string]any{"accepted": true, "hash": req.ReceiptHash, "anchored": false}
	common.WriteJSON(w, r, http.StatusOK, resp)
}

func (s *Server) handleSignedTreeHead(w http.ResponseWriter, r *http.Request) {
	resp := map[string]any{"treeSize": 0, "rootHash": "", "timestamp": "2025-08-31T11:41:30Z"}
	common.WriteJSON(w, r, http.StatusOK, resp)
}

func (s *Server) handleInclusionProof(w http.ResponseWriter, r *http.Request) {
	resp := map[string]any{"included": false}
	common.WriteJSON(w, r, http.StatusOK, resp)
}
