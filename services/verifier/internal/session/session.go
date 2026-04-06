package session

import (
	"crypto/ecdh"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"

	"github.com/cachet-id/cachet/services/verifier/internal/jwe"
)

// Session represents a verification session with a nonce for replay protection
// and an ephemeral X25519 key pair for end-to-end encryption.
type Session struct {
	ID              string           `json:"sessionId"`
	Nonce           string           `json:"nonce"`
	VerifierDID     string           `json:"verifierDid"`
	EphemeralPubKey string           `json:"ephemeralPubKey,omitempty"` // base64url X25519 public key
	CreatedAt       time.Time        `json:"-"`
	Used            bool             `json:"-"`
	ephemeralPriv   *ecdh.PrivateKey `json:"-"` // unexported, never serialized
}

// EphemeralPrivateKey returns the ephemeral X25519 private key for JWE decryption.
func (s *Session) EphemeralPrivateKey() *ecdh.PrivateKey {
	return s.ephemeralPriv
}

// Manager manages verification sessions with nonce generation and one-time-use.
type Manager struct {
	mu       sync.Mutex
	sessions map[string]*Session
	ttl      time.Duration
}

// NewManager creates a session manager with the given TTL.
func NewManager(ttl time.Duration) *Manager {
	return &Manager{
		sessions: make(map[string]*Session),
		ttl:      ttl,
	}
}

// Create generates a new verification session with a fresh nonce
// and an ephemeral X25519 key pair for end-to-end encryption.
func (m *Manager) Create(verifierDID string) *Session {
	nonce := generateNonce()

	priv, pubB64, err := jwe.GenerateEphemeralKeyPair()
	if err != nil {
		// Non-fatal: session works without E2E encryption
		priv = nil
		pubB64 = ""
	}

	s := &Session{
		ID:              uuid.New().String(),
		Nonce:           nonce,
		VerifierDID:     verifierDID,
		EphemeralPubKey: pubB64,
		CreatedAt:       time.Now(),
		ephemeralPriv:   priv,
	}

	m.mu.Lock()
	defer m.mu.Unlock()
	m.evictExpired()
	m.sessions[s.ID] = s
	return s
}

// Consume validates and consumes a session's nonce (one-time-use).
// Returns the session if valid, or an error if expired, already used, or not found.
func (m *Manager) Consume(sessionID string) (*Session, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	s, ok := m.sessions[sessionID]
	if !ok {
		return nil, fmt.Errorf("session not found: %s", sessionID)
	}

	if time.Since(s.CreatedAt) > m.ttl {
		delete(m.sessions, sessionID)
		return nil, fmt.Errorf("session expired")
	}

	if s.Used {
		return nil, fmt.Errorf("session nonce already consumed")
	}

	s.Used = true
	return s, nil
}

// Get retrieves a session without consuming it (for lookup).
func (m *Manager) Get(sessionID string) (*Session, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()

	s, ok := m.sessions[sessionID]
	if !ok {
		return nil, false
	}
	if time.Since(s.CreatedAt) > m.ttl {
		delete(m.sessions, sessionID)
		return nil, false
	}
	return s, true
}

func (m *Manager) evictExpired() {
	for id, s := range m.sessions {
		if time.Since(s.CreatedAt) > m.ttl {
			delete(m.sessions, id)
		}
	}
}

// generateNonce produces a 128-bit cryptographically random nonce, base64url-encoded.
func generateNonce() string {
	b := make([]byte, 16) // 128 bits
	if _, err := rand.Read(b); err != nil {
		panic("crypto/rand failed: " + err.Error())
	}
	return base64.RawURLEncoding.EncodeToString(b)
}
