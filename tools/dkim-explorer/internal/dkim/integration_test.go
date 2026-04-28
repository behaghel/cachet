//go:build integration

package dkim

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestIntegration_RealEmail reads a real .eml file from testdata/fixtures/
// and validates the full DKIM pipeline against live DNS.
//
// Run with: go test -tags=integration -v ./internal/dkim/ -run TestIntegration
//
// Prerequisites:
//   - Place a real .eml file at testdata/fixtures/real-email.eml
//   - The email must have a DKIM-Signature header
//   - DNS must be reachable
func TestIntegration_RealEmail(t *testing.T) {
	fixtureDir := filepath.Join("..", "..", "testdata", "fixtures")
	entries, err := os.ReadDir(fixtureDir)
	require.NoError(t, err)

	var realEmails []string
	for _, e := range entries {
		name := e.Name()
		if filepath.Ext(name) == ".eml" && name != "synthetic-booking.eml" {
			realEmails = append(realEmails, filepath.Join(fixtureDir, name))
		}
	}

	if len(realEmails) == 0 {
		t.Skip("no real .eml fixtures found in testdata/fixtures/ — add one and re-run")
	}

	for _, path := range realEmails {
		t.Run(filepath.Base(path), func(t *testing.T) {
			raw, err := os.ReadFile(path)
			require.NoError(t, err)

			// Step 1: Parse
			parsed, err := Parse(raw)
			require.NoError(t, err)
			t.Logf("From: %s", parsed.From)
			t.Logf("Subject: %s", parsed.Subject)
			t.Logf("Date: %s", parsed.Date)
			t.Logf("DKIM signatures found: %d", len(parsed.DKIMSignatures))

			require.NotEmpty(t, parsed.DKIMSignatures, "real email should have at least one DKIM signature")

			for i, sig := range parsed.DKIMSignatures {
				t.Logf("  [%d] d=%s s=%s a=%s", i, sig.Domain, sig.Selector, sig.Algorithm)
				t.Logf("       h=%v", sig.HeaderFields)
				if !sig.Timestamp.IsZero() {
					t.Logf("       t=%s", sig.Timestamp)
				}
			}

			// Step 2: Verify against real DNS
			results, err := Verify(raw, nil)
			require.NoError(t, err)

			for i, r := range results {
				status := "PASS"
				if !r.Valid {
					status = "FAIL"
				}
				t.Logf("Verification [%d]: %s domain=%s", i, status, r.Domain)
				if r.Err != nil {
					t.Logf("  Error: %v", r.Err)
				}
				t.Logf("  Signed headers: %v", r.HeaderKeys)
			}

			// At least one signature should be present
			assert.NotEmpty(t, results)

			// Log overall result
			anyValid := false
			for _, r := range results {
				if r.Valid {
					anyValid = true
				}
			}
			t.Logf("Any valid DKIM signature: %v", anyValid)
		})
	}
}
