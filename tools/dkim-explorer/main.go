package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/jhillyerd/enmime"

	"github.com/cachet-id/cachet/tools/dkim-explorer/internal/claims"
	"github.com/cachet-id/cachet/tools/dkim-explorer/internal/dkim"
)

func main() {
	if len(os.Args) < 2 {
		printUsage()
		os.Exit(1)
	}

	cmd := os.Args[1]
	fs := flag.NewFlagSet(cmd, flag.ExitOnError)
	jsonOutput := fs.Bool("json", false, "output as JSON")
	verbose := fs.Bool("verbose", false, "show raw DKIM header fields")
	fs.Parse(os.Args[2:])

	if fs.NArg() == 0 {
		fmt.Fprintf(os.Stderr, "error: provide an .eml file path or - for stdin\n")
		os.Exit(1)
	}

	raw, err := readInput(fs.Arg(0))
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}

	switch cmd {
	case "analyze":
		runAnalyze(raw, *jsonOutput, *verbose)
	case "parse":
		runParse(raw, *jsonOutput, *verbose)
	case "verify":
		runVerify(raw, *jsonOutput)
	case "claims":
		runClaims(raw, *jsonOutput)
	default:
		fmt.Fprintf(os.Stderr, "unknown command: %s\n", cmd)
		printUsage()
		os.Exit(1)
	}
}

func printUsage() {
	fmt.Fprintf(os.Stderr, `dkim-explorer — validate DKIM email proofs for behavioral evidence

Usage:
  dkim-explorer <command> [flags] <file.eml | ->

Commands:
  analyze    Full analysis: parse + verify + extract claims
  parse      Parse .eml and extract DKIM-Signature headers
  verify     Verify DKIM signature against DNS
  claims     Extract structured claims from email content

Flags:
  --json       Output as JSON
  --verbose    Show raw DKIM header fields
`)
}

func readInput(path string) ([]byte, error) {
	if path == "-" {
		return io.ReadAll(os.Stdin)
	}
	return os.ReadFile(path)
}

// AnalysisReport is the full analysis output.
type AnalysisReport struct {
	Parse    *dkim.ParseResult         `json:"parse"`
	Verify   []dkim.VerificationResult `json:"verify"`
	Evidence *claims.EmailEvidence     `json:"evidence"`
}

func runAnalyze(raw []byte, jsonOut, verbose bool) {
	parsed, err := dkim.Parse(raw)
	if err != nil {
		fmt.Fprintf(os.Stderr, "parse error: %v\n", err)
		os.Exit(1)
	}

	results, err := dkim.Verify(raw, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "verify error: %v\n", err)
		os.Exit(1)
	}

	textBody, htmlBody := extractBodies(raw)
	evidence := claims.Extract(parsed.From, parsed.Subject, textBody, htmlBody, parsed.Date)

	if jsonOut {
		report := AnalysisReport{Parse: parsed, Verify: results, Evidence: evidence}
		enc := json.NewEncoder(os.Stdout)
		enc.SetIndent("", "  ")
		enc.Encode(report)
		return
	}

	// Human-readable output
	fmt.Println("=== Email Analysis ===")
	fmt.Printf("From:    %s\n", parsed.From)
	fmt.Printf("To:      %s\n", parsed.To)
	fmt.Printf("Subject: %s\n", parsed.Subject)
	fmt.Printf("Date:    %s\n", parsed.Date.Format("2006-01-02 15:04:05"))
	fmt.Println()

	fmt.Printf("=== DKIM Signatures (%d found) ===\n", len(parsed.DKIMSignatures))
	for i, sig := range parsed.DKIMSignatures {
		fmt.Printf("[%d] Domain: %s  Selector: %s  Algorithm: %s\n", i+1, sig.Domain, sig.Selector, sig.Algorithm)
		if verbose {
			fmt.Printf("    Headers: %s\n", strings.Join(sig.HeaderFields, ", "))
			fmt.Printf("    BodyHash: %s\n", sig.BodyHash)
		}
	}
	fmt.Println()

	fmt.Printf("=== DKIM Verification (%d results) ===\n", len(results))
	for i, r := range results {
		status := "PASS"
		if !r.Valid {
			status = "FAIL"
		}
		fmt.Printf("[%d] %s  Domain: %s\n", i+1, status, r.Domain)
		if r.Err != nil {
			fmt.Printf("    Error: %v\n", r.Err)
		}
		if verbose {
			fmt.Printf("    Signed headers: %s\n", strings.Join(r.HeaderKeys, ", "))
		}
	}
	fmt.Println()

	fmt.Printf("=== Evidence ===\n")
	if evidence.Rejected {
		fmt.Printf("REJECTED: %s\n", evidence.RejectionReason)
		fmt.Printf("  Forwarded emails break the DKIM chain — only direct platform emails qualify as evidence.\n")
		return
	}
	if evidence.Platform != "" {
		fmt.Printf("Platform: %s\n", evidence.Platform)
	} else {
		fmt.Printf("Platform: (unknown)\n")
	}
	fmt.Printf("Claims: %d extracted\n", len(evidence.Claims))
	for i, c := range evidence.Claims {
		fmt.Printf("[%d] %s (confidence: %.0f%%, source: %s)\n", i+1, c.Type, c.Confidence*100, c.Source)
		for k, v := range c.Fields {
			fmt.Printf("    %s: %s\n", k, v)
		}
	}
}

func runParse(raw []byte, jsonOut, verbose bool) {
	parsed, err := dkim.Parse(raw)
	if err != nil {
		fmt.Fprintf(os.Stderr, "parse error: %v\n", err)
		os.Exit(1)
	}

	if jsonOut {
		enc := json.NewEncoder(os.Stdout)
		enc.SetIndent("", "  ")
		enc.Encode(parsed)
		return
	}

	fmt.Printf("From:    %s\n", parsed.From)
	fmt.Printf("To:      %s\n", parsed.To)
	fmt.Printf("Subject: %s\n", parsed.Subject)
	fmt.Printf("Date:    %s\n", parsed.Date.Format("2006-01-02 15:04:05"))
	fmt.Printf("DKIM Signatures: %d\n", len(parsed.DKIMSignatures))
	for i, sig := range parsed.DKIMSignatures {
		fmt.Printf("  [%d] d=%s s=%s a=%s\n", i+1, sig.Domain, sig.Selector, sig.Algorithm)
		if verbose {
			fmt.Printf("      h=%s\n", strings.Join(sig.HeaderFields, ":"))
			fmt.Printf("      bh=%s\n", sig.BodyHash)
		}
	}
}

func runVerify(raw []byte, jsonOut bool) {
	results, err := dkim.Verify(raw, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "verify error: %v\n", err)
		os.Exit(1)
	}

	if jsonOut {
		enc := json.NewEncoder(os.Stdout)
		enc.SetIndent("", "  ")
		enc.Encode(results)
		return
	}

	for i, r := range results {
		status := "PASS"
		if !r.Valid {
			status = "FAIL"
		}
		fmt.Printf("[%d] %s  domain=%s\n", i+1, status, r.Domain)
		if r.Err != nil {
			fmt.Printf("    error: %v\n", r.Err)
		}
	}
	if len(results) == 0 {
		fmt.Println("No DKIM signatures found.")
	}
}

func runClaims(raw []byte, jsonOut bool) {
	parsed, err := dkim.Parse(raw)
	if err != nil {
		fmt.Fprintf(os.Stderr, "parse error: %v\n", err)
		os.Exit(1)
	}

	textBody, htmlBody := extractBodies(raw)
	evidence := claims.Extract(parsed.From, parsed.Subject, textBody, htmlBody, parsed.Date)

	if jsonOut {
		enc := json.NewEncoder(os.Stdout)
		enc.SetIndent("", "  ")
		enc.Encode(evidence)
		return
	}

	if evidence.Rejected {
		fmt.Printf("REJECTED: %s\n", evidence.RejectionReason)
		return
	}
	if evidence.Platform != "" {
		fmt.Printf("Platform: %s\n", evidence.Platform)
	}
	for i, c := range evidence.Claims {
		fmt.Printf("[%d] %s (%.0f%%, %s)\n", i+1, c.Type, c.Confidence*100, c.Source)
		for k, v := range c.Fields {
			fmt.Printf("    %s: %s\n", k, v)
		}
	}
	if len(evidence.Claims) == 0 {
		fmt.Println("No claims extracted.")
	}
}

// extractBodies uses enmime to properly parse MIME multipart emails
// and decode quoted-printable/base64 content.
func extractBodies(raw []byte) (textBody, htmlBody string) {
	env, err := enmime.ReadEnvelope(bytes.NewReader(raw))
	if err != nil {
		// Fallback: naive extraction for non-MIME emails
		s := string(raw)
		idx := strings.Index(s, "\n\n")
		if idx < 0 {
			idx = strings.Index(s, "\r\n\r\n")
		}
		if idx >= 0 {
			return strings.TrimSpace(s[idx:]), ""
		}
		return "", ""
	}
	return env.Text, env.HTML
}
