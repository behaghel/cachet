package dkim

import (
	"bytes"

	godkim "github.com/emersion/go-msgauth/dkim"
)

// VerificationResult wraps the outcome of a single DKIM signature verification.
type VerificationResult struct {
	Domain     string // signing domain (d= tag)
	Identifier string // AUID (i= tag)
	HeaderKeys []string
	Valid      bool
	Err        error
}

// VerifyOptions configures DKIM verification.
type VerifyOptions struct {
	// LookupTXT overrides DNS resolution for testing.
	// If nil, net.LookupTXT is used (real DNS).
	LookupTXT func(domain string) ([]string, error)
}

// Verify checks all DKIM signatures in raw email bytes.
// Returns one VerificationResult per DKIM-Signature header found.
func Verify(raw []byte, opts *VerifyOptions) ([]VerificationResult, error) {
	var gopts *godkim.VerifyOptions
	if opts != nil && opts.LookupTXT != nil {
		gopts = &godkim.VerifyOptions{
			LookupTXT: opts.LookupTXT,
		}
	}

	verifications, err := godkim.VerifyWithOptions(bytes.NewReader(raw), gopts)
	if err != nil {
		return nil, err
	}

	results := make([]VerificationResult, len(verifications))
	for i, v := range verifications {
		results[i] = VerificationResult{
			Domain:     v.Domain,
			Identifier: v.Identifier,
			HeaderKeys: v.HeaderKeys,
			Valid:      v.Err == nil,
			Err:        v.Err,
		}
	}
	return results, nil
}
