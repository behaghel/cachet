package statuslist

import (
	"bytes"
	"compress/gzip"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"
)

// Checker fetches and caches StatusList2021 bitstrings, then checks revocation.
type Checker struct {
	mu         sync.RWMutex
	cache      map[string]*cachedList
	httpClient *http.Client
	ttl        time.Duration
}

type cachedList struct {
	bits      []byte
	fetchedAt time.Time
}

// NewChecker creates a checker with a 5-minute cache TTL.
func NewChecker() *Checker {
	return &Checker{
		cache:      make(map[string]*cachedList),
		httpClient: &http.Client{Timeout: 10 * time.Second},
		ttl:        5 * time.Minute,
	}
}

// IsRevoked checks whether the credential at the given index is revoked.
// Fetches and caches the bitstring from the statusListCredential URL.
func (c *Checker) IsRevoked(statusListURL string, index int) (bool, error) {
	bits, err := c.getBits(statusListURL)
	if err != nil {
		return false, fmt.Errorf("fetch status list: %w", err)
	}

	byteIdx := index / 8
	bitIdx := 7 - (index % 8) // MSB first per W3C spec
	if byteIdx >= len(bits) {
		return false, fmt.Errorf("index %d out of range for status list", index)
	}
	return (bits[byteIdx]>>bitIdx)&1 == 1, nil
}

func (c *Checker) getBits(url string) ([]byte, error) {
	c.mu.RLock()
	cached, ok := c.cache[url]
	c.mu.RUnlock()

	if ok && time.Since(cached.fetchedAt) < c.ttl {
		return cached.bits, nil
	}

	return c.fetchAndCache(url)
}

func (c *Checker) fetchAndCache(url string) ([]byte, error) {
	resp, err := c.httpClient.Get(url)
	if err != nil {
		return nil, fmt.Errorf("HTTP GET %s: %w", url, err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP GET %s returned %d", url, resp.StatusCode)
	}

	var body struct {
		EncodedList string `json:"encodedList"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, fmt.Errorf("decode status list response: %w", err)
	}

	bits, err := decodeBitstring(body.EncodedList)
	if err != nil {
		return nil, fmt.Errorf("decode bitstring: %w", err)
	}

	c.mu.Lock()
	c.cache[url] = &cachedList{bits: bits, fetchedAt: time.Now()}
	c.mu.Unlock()

	return bits, nil
}

// decodeBitstring decodes base64url(gzip(bytes)) into raw bytes.
func decodeBitstring(encoded string) ([]byte, error) {
	compressed, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		return nil, fmt.Errorf("base64url decode: %w", err)
	}
	gz, err := gzip.NewReader(bytes.NewReader(compressed))
	if err != nil {
		return nil, fmt.Errorf("gzip reader: %w", err)
	}
	defer func() { _ = gz.Close() }()
	return io.ReadAll(gz)
}
