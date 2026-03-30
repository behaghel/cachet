package main

import "github.com/cachet-id/cachet/services/common"

func main() {
	common.InitLogging()

	cfg := common.ServerConfig{
		Name:    "issuance-gateway",
		Version: "0.1.0",
		Port:    "8090",
	}

	server := NewServer()
	common.ListenAndServe(server.router, cfg)
}
