package main

import (
	"os"

	"github.com/cachet-id/cachet/services/common"
)

func main() {
	common.InitLogging()

	cfg := DefaultServerConfig()
	if secret := os.Getenv("VERIFF_WEBHOOK_SECRET"); secret != "" {
		cfg.WebhookSecret = secret
	}
	server := NewServerWithConfig(cfg)
	common.ListenAndServe(server.Router(), cfg.Common)
}
