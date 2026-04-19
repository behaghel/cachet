package main

import (
	"github.com/cachet-id/cachet/services/common"
	"go.opentelemetry.io/otel/metric"
)

// Metrics instruments for the relay service.
var (
	relaySessionsTotal   metric.Int64Counter
	relayResponseLatency metric.Float64Histogram
)

func init() {
	meter := common.Meter("relay")

	relaySessionsTotal, _ = meter.Int64Counter("cachet.relay.sessions.total",
		metric.WithDescription("Relay sessions by lifecycle status"),
		metric.WithUnit("{session}"),
	)
	relayResponseLatency, _ = meter.Float64Histogram("cachet.relay.holder_response_time_seconds",
		metric.WithDescription("Time from session creation to holder response"),
		metric.WithUnit("s"),
	)
}
