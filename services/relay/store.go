package main

import (
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"
)

// RelaySession holds request and response payloads for a verification session.
// The relay treats both as opaque bytes — it never interprets them.
type RelaySession struct {
	ID        string    `json:"sessionId"`
	Request   []byte    `json:"-"` // signed Request Object (opaque to relay)
	Response  []byte    `json:"-"` // encrypted VP (opaque to relay)
	CreatedAt time.Time `json:"-"`
}

// SessionStore is a thread-safe in-memory store with TTL eviction.
type SessionStore struct {
	mu       sync.RWMutex
	sessions map[string]*RelaySession
	ttl      time.Duration
}

// NewSessionStore creates a store with the given TTL.
func NewSessionStore(ttl time.Duration) *SessionStore {
	return &SessionStore{
		sessions: make(map[string]*RelaySession),
		ttl:      ttl,
	}
}

// Create stores a request payload and returns a new session.
func (s *SessionStore) Create(request []byte) *RelaySession {
	sess := &RelaySession{
		ID:        uuid.New().String(),
		Request:   request,
		CreatedAt: time.Now(),
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.evictExpired()
	s.sessions[sess.ID] = sess
	return sess
}

// GetRequest returns the request payload for a session.
func (s *SessionStore) GetRequest(id string) ([]byte, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	sess, ok := s.sessions[id]
	if !ok {
		return nil, fmt.Errorf("session not found")
	}
	if time.Since(sess.CreatedAt) > s.ttl {
		return nil, fmt.Errorf("session expired")
	}
	return sess.Request, nil
}

// SetResponse stores the response payload (encrypted VP) for a session.
func (s *SessionStore) SetResponse(id string, response []byte) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	sess, ok := s.sessions[id]
	if !ok {
		return fmt.Errorf("session not found")
	}
	if time.Since(sess.CreatedAt) > s.ttl {
		delete(s.sessions, id)
		return fmt.Errorf("session expired")
	}
	sess.Response = response
	return nil
}

// GetResponse returns the response payload if available.
// Returns nil, nil if the holder hasn't responded yet (verifier is polling).
func (s *SessionStore) GetResponse(id string) ([]byte, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	sess, ok := s.sessions[id]
	if !ok {
		return nil, fmt.Errorf("session not found")
	}
	if time.Since(sess.CreatedAt) > s.ttl {
		return nil, fmt.Errorf("session expired")
	}
	return sess.Response, nil // nil if not yet posted
}

func (s *SessionStore) evictExpired() {
	for id, sess := range s.sessions {
		if time.Since(sess.CreatedAt) > s.ttl {
			delete(s.sessions, id)
		}
	}
}
