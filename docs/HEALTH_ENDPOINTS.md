# Health Endpoints in Cachet Services

## Summary

All Cachet services use `/health` endpoints for health checks, not `/healthz`. This is due to Cloud Run infrastructure limitations.

## The Problem

Cloud Run's infrastructure intercepts requests to `/healthz` and returns Google's own 404 pages instead of forwarding the requests to your application. This causes health checks to fail with a Google-branded 404 response rather than your application's health check response.

## The Solution

**Use `/health` instead of `/healthz` for all health endpoints.**

### Current Implementation

All services now implement health checks at `/health`:

- Verifier service: `GET /health`
- Registry service: `GET /health` 
- Receipts Log service: `GET /health`
- Issuance Gateway service: `GET /health`
- Transparency Log service: `GET /health`
- Connector Hub service: `GET /health`
- Vouching service: `GET /health`

### Example Implementation

```go
func (s *Server) setupRoutes() {
    // Note: /healthz is reserved by Cloud Run infrastructure - use /health instead
    s.router.Get("/health", s.handleHealth)
    // ... other routes
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
    w.WriteHeader(http.StatusOK)
    if _, err := w.Write([]byte("ok")); err != nil {
        log.Error().Err(err).Msg("Failed to write health check response")
    }
}
```

## Prevention Safeguards

To prevent accidental use of `/healthz` in the future, multiple safeguards have been implemented:

### 1. Pre-commit Hook
- **File**: `.pre-commit-config.yaml` and `devenv.nix` git-hooks
- **Script**: `scripts/check-healthz.sh`
- **Action**: Blocks commits containing `/healthz` endpoints

### 2. CI/CD Pipeline Check  
- **File**: `.github/workflows/healthz-check.yml`
- **Action**: Fails CI builds if `/healthz` endpoints are found
- **Feedback**: Automatically comments on PRs with fixing instructions

### 3. Manual Check Script
```bash
./scripts/check-healthz.sh
```

### 4. Integration Tests
All integration tests use `/health` endpoints:
```bash
curl -f http://localhost:8081/health  # Verifier
curl -f http://localhost:8082/health  # Registry  
curl -f http://localhost:8083/health  # Receipts
curl -f http://localhost:8090/health  # Issuance Gateway
```

## Testing

All services have corresponding tests that verify the `/health` endpoint:

```go
func TestHealthCheck(t *testing.T) {
    server := NewServer()
    req := httptest.NewRequest(http.MethodGet, "/health", nil)
    w := httptest.NewRecorder()
    
    server.router.ServeHTTP(w, req)
    
    assert.Equal(t, http.StatusOK, w.Code)
    assert.Equal(t, "ok", w.Body.String())
}
```

## Related Infrastructure

### Cloud Run
- Cloud Run intercepts `/healthz` at the infrastructure level
- Use `/health` for application health checks
- Configure Cloud Run health checks to use `/health`

### Load Balancers
- Configure health check probes to use `/health` 
- Update any monitoring systems to use `/health`

### Kubernetes (if applicable)
```yaml
livenessProbe:
  httpGet:
    path: /health  # Not /healthz
    port: 8080
readinessProbe:
  httpGet:
    path: /health  # Not /healthz  
    port: 8080
```

## Migration Checklist

If you find existing `/healthz` endpoints, follow this checklist:

- [ ] Replace `/healthz` routes with `/health` routes in Go code
- [ ] Update corresponding tests to use `/health`
- [ ] Update integration tests and scripts
- [ ] Update documentation and deployment configurations
- [ ] Add explanatory comments about Cloud Run limitation
- [ ] Run `./scripts/check-healthz.sh` to verify no `/healthz` remains
- [ ] Test that health checks work correctly

## Monitoring

All health endpoints should return:
- **Status Code**: `200 OK`
- **Response Body**: `"ok"`
- **Content-Type**: `text/plain` (implicit)

Monitor these endpoints for:
- Response time < 100ms
- 99.9% availability  
- Proper HTTP status codes

## References

- [Cloud Run Health Checks Documentation](https://cloud.google.com/run/docs/configuring/healthchecks)
- [HTTP Health Check Best Practices](https://cloud.google.com/load-balancing/docs/health-check-concepts)