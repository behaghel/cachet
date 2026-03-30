package main

import "github.com/cachet-id/cachet/services/common"

func main() {
	common.InitLogging()

	cfg := DefaultServerConfig()
	server := NewServerWithConfig(cfg)
	common.ListenAndServe(server.Router(), cfg.Common)
}
