package common

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/rs/zerolog"
)

func TestAuditLog_StructuredOutput(t *testing.T) {
	var buf bytes.Buffer
	logger := zerolog.New(&buf)

	req := httptest.NewRequest(http.MethodPost, "/admin/packs", nil)
	req = req.WithContext(logger.WithContext(req.Context()))
	AuditLog(req, "pack.created", "pack.childcare.readiness", "success")

	var entry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &entry); err != nil {
		t.Fatalf("failed to parse log output: %v\n%s", err, buf.String())
	}

	if entry["audit"] != true {
		t.Errorf("expected audit=true, got %v", entry["audit"])
	}
	if entry["action"] != "pack.created" {
		t.Errorf("expected action=pack.created, got %v", entry["action"])
	}
	if entry["resource"] != "pack.childcare.readiness" {
		t.Errorf("expected resource=pack.childcare.readiness, got %v", entry["resource"])
	}
	if entry["outcome"] != "success" {
		t.Errorf("expected outcome=success, got %v", entry["outcome"])
	}
	if _, ok := entry["timestamp"]; !ok {
		t.Error("expected timestamp field")
	}
}

func TestAuditMiddleware_MutatingRequest(t *testing.T) {
	var buf bytes.Buffer
	logger := zerolog.New(&buf)

	inner := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
	})

	handler := AuditMiddleware(inner)
	req := httptest.NewRequest(http.MethodPost, "/admin/packs", nil)
	req = req.WithContext(logger.WithContext(req.Context()))
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	var entry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &entry); err != nil {
		t.Fatalf("failed to parse log output: %v\n%s", err, buf.String())
	}

	if entry["audit"] != true {
		t.Errorf("expected audit=true")
	}
	if entry["action"] != "POST /admin/packs" {
		t.Errorf("expected action='POST /admin/packs', got %v", entry["action"])
	}
	if entry["outcome"] != "success" {
		t.Errorf("expected outcome=success, got %v", entry["outcome"])
	}
}

func TestAuditMiddleware_GetNotAudited(t *testing.T) {
	var buf bytes.Buffer
	logger := zerolog.New(&buf)

	inner := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	handler := AuditMiddleware(inner)
	req := httptest.NewRequest(http.MethodGet, "/admin/packs", nil)
	req = req.WithContext(logger.WithContext(req.Context()))
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if buf.Len() != 0 {
		t.Errorf("GET requests should not be audited, got: %s", buf.String())
	}
}

func TestAuditMiddleware_FailureOutcome(t *testing.T) {
	var buf bytes.Buffer
	logger := zerolog.New(&buf)

	inner := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
	})

	handler := AuditMiddleware(inner)
	req := httptest.NewRequest(http.MethodDelete, "/admin/sessions/123", nil)
	req = req.WithContext(logger.WithContext(req.Context()))
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	var entry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &entry); err != nil {
		t.Fatalf("failed to parse log output: %v\n%s", err, buf.String())
	}

	if entry["outcome"] != "failure" {
		t.Errorf("expected outcome=failure, got %v", entry["outcome"])
	}
}
