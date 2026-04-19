package main

import (
	"context"
	"fmt"
	"os"

	"github.com/cachet-id/cachet/services/common"
)

func main() {
	common.InitLogging()

	cfg := DefaultServerConfig()
	if secret := os.Getenv("VERIFF_WEBHOOK_SECRET"); secret != "" {
		cfg.WebhookSecret = secret
	}

	cfg.Common.ReadinessChecks = []common.ReadinessCheck{
		func(_ context.Context) error {
			if cfg.IssuerKey == nil {
				return fmt.Errorf("issuer key not loaded")
			}
			return nil
		},
	}

	server := NewServerWithConfig(cfg)
	common.ListenAndServe(server.Router(), cfg.Common)
}
