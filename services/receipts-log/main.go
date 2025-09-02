package main

import (
	"encoding/json"
	"github.com/go-chi/chi/v5"
	"net/http"
	"os"
)

type submit struct {
	ReceiptHash string `json:"receiptHash"`
}

func main() {
	r := chi.NewRouter()
	r.Get("/healthz", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })
	r.Post("/receipts/hash", func(w http.ResponseWriter, r *http.Request) {
		var s submit
		json.NewDecoder(r.Body).Decode(&s)
		resp := map[string]any{"accepted": true, "hash": s.ReceiptHash, "anchored": false}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	})
	r.Get("/log/sth", func(w http.ResponseWriter, r *http.Request) {
		resp := map[string]any{"treeSize": 0, "rootHash": "", "timestamp": "2025-08-31T11:41:30Z"}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	})
	r.Get("/log/proof", func(w http.ResponseWriter, r *http.Request) {
		resp := map[string]any{"included": false}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	})
	port := os.Getenv("PORT")
	if port == "" {
		port = "8083"
	}
	http.ListenAndServe(":"+port, r)
}
