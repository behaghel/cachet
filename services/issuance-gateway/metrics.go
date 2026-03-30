package main

import (
	"github.com/cachet-id/cachet/services/common"
	"go.opentelemetry.io/otel/metric"
)

// Metrics instruments for the issuance gateway.
var (
	credentialsIssued metric.Int64Counter
	webhooksReceived  metric.Int64Counter
	webhooksStored    metric.Int64Counter
	qualityTierGauge  metric.Int64Counter
)

func init() {
	meter := common.Meter("issuance-gateway")

	credentialsIssued, _ = meter.Int64Counter("cachet.credentials.issued",
		metric.WithDescription("Total credentials issued"),
		metric.WithUnit("{credential}"),
	)
	webhooksReceived, _ = meter.Int64Counter("cachet.webhooks.received",
		metric.WithDescription("Total Veriff webhooks received"),
		metric.WithUnit("{webhook}"),
	)
	webhooksStored, _ = meter.Int64Counter("cachet.webhooks.stored",
		metric.WithDescription("Webhooks that passed validation and were stored"),
		metric.WithUnit("{webhook}"),
	)
	qualityTierGauge, _ = meter.Int64Counter("cachet.quality_tier",
		metric.WithDescription("Credentials issued by quality tier"),
		metric.WithUnit("{credential}"),
	)
}
