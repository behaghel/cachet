package dkim

import (
	"bytes"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"fmt"
	"testing"

	godkim "github.com/emersion/go-msgauth/dkim"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// signTestEmail creates a DKIM-signed email for testing.
// Returns the raw signed email bytes and the DNS TXT record value for verification.
func signTestEmail(t *testing.T, domain, selector, body string) ([]byte, string) {
	t.Helper()

	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)

	msg := fmt.Sprintf("From: noreply@%s\r\nTo: test@example.com\r\nSubject: Test Booking\r\nDate: Sat, 25 Apr 2026 10:00:00 +0000\r\n\r\n%s", domain, body)

	var signed bytes.Buffer
	err = godkim.Sign(&signed, bytes.NewReader([]byte(msg)), &godkim.SignOptions{
		Domain:                 domain,
		Selector:               selector,
		Signer:                 key,
		Hash:                   crypto.SHA256,
		HeaderCanonicalization: godkim.CanonicalizationRelaxed,
		BodyCanonicalization:   godkim.CanonicalizationRelaxed,
		HeaderKeys:             []string{"From", "To", "Subject", "Date"},
	})
	require.NoError(t, err)

	// Build DNS TXT record: "v=DKIM1; k=rsa; p=<base64 PKIX DER public key>"
	der, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	require.NoError(t, err)
	dnsRecord := fmt.Sprintf("v=DKIM1; k=rsa; p=%s", base64.StdEncoding.EncodeToString(der))

	return signed.Bytes(), dnsRecord
}

func TestVerify_SignedEmail(t *testing.T) {
	signed, dnsRecord := signTestEmail(t, "test.example.com", "sel1", "Hello, this is a test booking confirmation.\r\n")

	mockDNS := func(domain string) ([]string, error) {
		if domain == "sel1._domainkey.test.example.com" {
			return []string{dnsRecord}, nil
		}
		return nil, fmt.Errorf("no DNS record for %s", domain)
	}

	results, err := Verify(signed, &VerifyOptions{LookupTXT: mockDNS})
	require.NoError(t, err)
	require.Len(t, results, 1)

	assert.True(t, results[0].Valid, "DKIM signature should be valid, got: %v", results[0].Err)
	assert.Nil(t, results[0].Err)
	assert.Equal(t, "test.example.com", results[0].Domain)
	assert.Contains(t, results[0].HeaderKeys, "From")
}

func TestVerify_TamperedBody(t *testing.T) {
	signed, dnsRecord := signTestEmail(t, "test.example.com", "sel1", "Original body content.\r\n")

	// Tamper with the body after signing
	tampered := bytes.Replace(signed, []byte("Original body content."), []byte("TAMPERED body content!"), 1)

	mockDNS := func(domain string) ([]string, error) {
		if domain == "sel1._domainkey.test.example.com" {
			return []string{dnsRecord}, nil
		}
		return nil, fmt.Errorf("no DNS record for %s", domain)
	}

	results, err := Verify(tampered, &VerifyOptions{LookupTXT: mockDNS})
	require.NoError(t, err)
	require.Len(t, results, 1)

	assert.False(t, results[0].Valid, "DKIM should fail for tampered body")
	assert.Error(t, results[0].Err)
}

func TestVerify_NoDKIMSignature(t *testing.T) {
	raw := []byte("From: test@example.com\r\nTo: bob@example.com\r\nSubject: No DKIM\r\n\r\nPlain email.\r\n")

	results, err := Verify(raw, nil)
	require.NoError(t, err)
	assert.Empty(t, results, "email without DKIM-Signature should return no results")
}

func TestVerify_DNSFailure(t *testing.T) {
	signed, _ := signTestEmail(t, "test.example.com", "sel1", "Test body.\r\n")

	failDNS := func(domain string) ([]string, error) {
		return nil, fmt.Errorf("DNS lookup failed")
	}

	results, err := Verify(signed, &VerifyOptions{LookupTXT: failDNS})
	require.NoError(t, err)
	require.Len(t, results, 1)

	assert.False(t, results[0].Valid, "DKIM should fail when DNS is unavailable")
	assert.Error(t, results[0].Err)
}
