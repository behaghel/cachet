package main

import (
	"github.com/go-chi/chi/v5"
	"net/http"
	"os"
)

func main() {
	r := chi.NewRouter()
	r.Get("/healthz", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })
	p := os.Getenv("PORT")
	if p == "" {
		p = "8090"
	}
	http.ListenAndServe(":"+p, r)
}
