# Monitoring

Cachet uses GCP Cloud Monitoring for production observability. Metrics are exposed via Prometheus `/metrics` endpoints on each service, scraped by GCP Managed Prometheus.

## Setup

```bash
devenv shell -- gcp:monitoring:setup    # Create dashboard + alert policies
devenv shell -- gcp:monitoring:status   # Verify what's provisioned
```

Prerequisite: `gcp:setup` must have been run first (enables monitoring API).

## Dashboard

The **Cachet Operations** dashboard (`infra/monitoring/dashboard.json`) has four sections:

### Service Health (Cloud Run built-in metrics)
- **Request Rate by Service** — time series of req/s per service
- **Error Rate (5xx)** — stacked area of server errors per service
- **Latency (p50/p95/p99)** — response time percentiles
- **Instance Count** — Cloud Run autoscaler activity

### Verification & Issuance (Prometheus custom metrics)
- **Verifications by Outcome** — pass/fail/error breakdown
- **Verification Duration** — p50 and p95 histogram
- **Credentials Issued** — issuance rate

### Pack & Relay (Prometheus custom metrics)
- **Pack Popularity** — which packs are requested most, by pack_id
- **Relay Session Lifecycle** — created/completed session rates

## Alert Policies

Four alert policies are defined in `infra/monitoring/alerts.json`:

| Alert | Condition | Window |
|-------|-----------|--------|
| **High Error Rate** | 5xx rate > 1% of total requests | 5 min |
| **High Latency** | p95 > 2000ms | 5 min |
| **Verification Failure Spike** | fail rate > 20% | 15 min |
| **Service Down** | Zero requests received | 10 min |

Alerts auto-close after 30 minutes (1 hour for verification spike).

## Notification Channels

Alert policies are created without notification channels. After running `gcp:monitoring:setup`, configure a channel in the GCP Console:

1. Go to **Monitoring > Alerting > Notification channels**
2. Add an email, Slack, or PagerDuty channel
3. Edit each alert policy to add the channel

## Custom Metrics

These are the Prometheus metrics exposed by Cachet services on `/metrics`:

### Verifier
| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `cachet_verifications_total` | counter | pack_id, status | Verification outcomes |
| `cachet_verifications_duration_seconds` | histogram | pack_id, status | Time to result |
| `cachet_packs_requested` | counter | pack_id | Pack fetch requests |
| `cachet_sessions_created` | counter | — | Verification sessions |

### Relay
| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `cachet_relay_sessions_total` | counter | status | Session lifecycle |
| `cachet_relay_holder_response_time_seconds` | histogram | — | Holder response latency |

### Issuance Gateway
| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `cachet_credentials_issued` | counter | — | Credentials issued |
| `cachet_webhooks_received` | counter | status | Veriff webhooks |
| `cachet_webhooks_stored` | counter | — | Validated webhooks |
| `cachet_quality_tier` | counter | tier | By quality tier |

## Prometheus Scraping on Cloud Run

GCP Managed Prometheus scrapes the `/metrics` endpoint automatically when configured. Add this annotation to Cloud Run service metadata:

```yaml
metadata:
  annotations:
    run.googleapis.com/launch-stage: BETA
spec:
  template:
    metadata:
      annotations:
        run.googleapis.com/container-dependencies: '{"sidecar":["app"]}'
```

Or use the sidecar-less approach with Cloud Run Jobs for scraping. See [GCP documentation](https://cloud.google.com/run/docs/monitoring).
