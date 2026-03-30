package main

import "github.com/cachet-id/cachet/services/common"

func main() {
	common.InitLogging()

	cfg := common.ServerConfig{
		Name:    "verifier",
		Version: "0.1.0",
		Port:    "8081",
	}

	server := NewServer(cfg)
	common.ListenAndServe(server.Router(), cfg)
}
