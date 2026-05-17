package dkim

import (
	"net/mail"
	"strings"
	"time"
)

// DKIMInfo holds parsed DKIM-Signature header fields.
type DKIMInfo struct {
	Domain       string   // d= tag (signing domain)
	Selector     string   // s= tag (DNS selector)
	Algorithm    string   // a= tag (e.g., "rsa-sha256")
	HeaderFields []string // h= tag (list of signed headers)
	BodyHash     string   // bh= tag (base64 body hash)
	Signature    string   // b= tag (base64 signature)
	Timestamp    time.Time
	Expiration   time.Time
}

// ParseResult holds the output of parsing a raw email.
type ParseResult struct {
	DKIMSignatures []DKIMInfo  `json:"dkimSignatures"`
	From           string      `json:"from"`
	To             string      `json:"to"`
	Subject        string      `json:"subject"`
	Date           time.Time   `json:"date"`
	MessageID      string      `json:"messageId"`
	RawHeaders     mail.Header `json:"-"`
	RawBytes       []byte      `json:"-"` // preserved for DKIM verification, excluded from JSON
}

// Parse reads raw email bytes and extracts DKIM signature info and envelope headers.
func Parse(raw []byte) (*ParseResult, error) {
	msg, err := mail.ReadMessage(strings.NewReader(string(raw)))
	if err != nil {
		return nil, err
	}

	result := &ParseResult{
		RawBytes:   raw,
		RawHeaders: msg.Header,
		From:       msg.Header.Get("From"),
		To:         msg.Header.Get("To"),
		Subject:    msg.Header.Get("Subject"),
		MessageID:  msg.Header.Get("Message-ID"),
	}

	if d, err := msg.Header.Date(); err == nil {
		result.Date = d
	}

	// Extract all DKIM-Signature headers
	for _, val := range msg.Header["Dkim-Signature"] {
		info := parseDKIMSignatureValue(val)
		result.DKIMSignatures = append(result.DKIMSignatures, info)
	}

	return result, nil
}

// parseDKIMSignatureValue parses a DKIM-Signature header value into DKIMInfo.
// Format: tag=value pairs separated by ";".
func parseDKIMSignatureValue(value string) DKIMInfo {
	tags := make(map[string]string)
	for _, part := range strings.Split(value, ";") {
		part = strings.TrimSpace(part)
		if idx := strings.Index(part, "="); idx > 0 {
			key := strings.TrimSpace(part[:idx])
			val := strings.TrimSpace(part[idx+1:])
			// DKIM values can contain folded whitespace — collapse it
			val = strings.Join(strings.Fields(val), "")
			tags[key] = val
		}
	}

	info := DKIMInfo{
		Domain:    tags["d"],
		Selector:  tags["s"],
		Algorithm: tags["a"],
		BodyHash:  tags["bh"],
		Signature: tags["b"],
	}

	if h, ok := tags["h"]; ok {
		for _, field := range strings.Split(h, ":") {
			field = strings.TrimSpace(field)
			if field != "" {
				info.HeaderFields = append(info.HeaderFields, field)
			}
		}
	}

	if t, ok := tags["t"]; ok {
		info.Timestamp = parseUnixTimestamp(t)
	}
	if x, ok := tags["x"]; ok {
		info.Expiration = parseUnixTimestamp(x)
	}

	return info
}

func parseUnixTimestamp(s string) time.Time {
	s = strings.TrimSpace(s)
	var ts int64
	for _, c := range s {
		if c >= '0' && c <= '9' {
			ts = ts*10 + int64(c-'0')
		}
	}
	if ts == 0 {
		return time.Time{}
	}
	return time.Unix(ts, 0)
}
