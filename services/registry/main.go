package main

import (
	"os"

	"github.com/cachet-id/cachet/services/common"
)

func main() {
	common.InitLogging()

	cfg := common.ServerConfig{
		Name:    "registry",
		Version: "0.1.0",
		Port:    "8082",
	}

	overlayDir := os.Getenv("CACHET_PACK_OVERLAY_DIR")
	server := NewServerWithOverlay(cfg, overlayDir)
	common.ListenAndServe(server.Router(), cfg)
}
