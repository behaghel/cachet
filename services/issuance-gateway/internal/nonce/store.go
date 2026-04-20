// Package nonce provides a single-use, TTL-evicting c_nonce store
// for OpenID4VCI proof replay prevention (T15 mitigation).
package nonce

import (
	"crypto/rand"
	"encoding/base64"
	"sync"
	"time"
)

const (
	// DefaultTTL is the default nonce lifetime (5 minutes per OpenID4VCI spec).
	DefaultTTL = 5 * time.Minute

	// DefaultMaxSize is the maximum number of nonces stored before eviction.
	DefaultMaxSize = 10000

	// NonceBytes is the entropy size for generated nonces (128 bits).
	NonceBytes = 16
)

// Store manages single-use c_nonces for the credential issuance endpoint.
// Each nonce is valid for one use within its TTL window.
type Store struct {
	mu      sync.Mutex
	nonces  map[string]time.Time // nonce → created_at
	ttl     time.Duration
	maxSize int
	now     func() time.Time
}

// Option configures the nonce Store.
type Option func(*Store)

// WithTTL sets the nonce lifetime.
func WithTTL(d time.Duration) Option {
	return func(s *Store) { s.ttl = d }
}

// WithMaxSize sets the maximum number of active nonces.
func WithMaxSize(n int) Option {
	return func(s *Store) { s.maxSize = n }
}

// WithClock injects a time source for testing.
func WithClock(now func() time.Time) Option {
	return func(s *Store) { s.now = now }
}

// NewStore creates a nonce store with the given options.
func NewStore(opts ...Option) *Store {
	s := &Store{
		nonces:  make(map[string]time.Time),
		ttl:     DefaultTTL,
		maxSize: DefaultMaxSize,
		now:     time.Now,
	}
	for _, opt := range opts {
		opt(s)
	}
	return s
}

// Issue generates a new c_nonce and stores it. Returns the nonce string
// and its expiration time in seconds.
func (s *Store) Issue() (string, int) {
	nonce := generateNonce()

	s.mu.Lock()
	defer s.mu.Unlock()

	s.evictExpiredLocked()
	if len(s.nonces) >= s.maxSize {
		s.evictOldestLocked()
	}

	s.nonces[nonce] = s.now()
	return nonce, int(s.ttl.Seconds())
}

// Consume validates and consumes a nonce. Returns true if the nonce was
// valid and fresh. The nonce is deleted regardless (single-use).
// This is the core T15 mitigation: each nonce can only be used once.
func (s *Store) Consume(nonce string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()

	createdAt, exists := s.nonces[nonce]
	if !exists {
		return false
	}

	// Always delete — single-use regardless of validity
	delete(s.nonces, nonce)

	// Check TTL
	if s.now().Sub(createdAt) > s.ttl {
		return false
	}

	return true
}

// TTLSeconds returns the configured TTL in seconds (for the API response).
func (s *Store) TTLSeconds() int {
	return int(s.ttl.Seconds())
}

func generateNonce() string {
	b := make([]byte, NonceBytes)
	if _, err := rand.Read(b); err != nil {
		panic("crypto/rand failed: " + err.Error())
	}
	return base64.RawURLEncoding.EncodeToString(b)
}

func (s *Store) evictExpiredLocked() {
	now := s.now()
	for n, t := range s.nonces {
		if now.Sub(t) > s.ttl {
			delete(s.nonces, n)
		}
	}
}

func (s *Store) evictOldestLocked() {
	var oldestNonce string
	var oldestTime time.Time
	for n, t := range s.nonces {
		if oldestNonce == "" || t.Before(oldestTime) {
			oldestNonce = n
			oldestTime = t
		}
	}
	if oldestNonce != "" {
		delete(s.nonces, oldestNonce)
	}
}
