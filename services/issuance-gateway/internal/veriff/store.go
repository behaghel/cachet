package veriff

import (
	"sync"
	"time"
)

// SessionStore stores verified Veriff sessions.
type SessionStore interface {
	Put(session Session)
	Get(sessionID string) (Session, bool)
}

type timedSession struct {
	session  Session
	storedAt time.Time
}

// InMemoryStore is a thread-safe, bounded, TTL-evicting in-memory session store.
type InMemoryStore struct {
	mu       sync.RWMutex
	sessions map[string]timedSession
	maxSize  int
	ttl      time.Duration
	now      func() time.Time // for testing
}

// StoreOption configures InMemoryStore.
type StoreOption func(*InMemoryStore)

// WithMaxSize sets the maximum number of sessions stored.
func WithMaxSize(n int) StoreOption {
	return func(s *InMemoryStore) { s.maxSize = n }
}

// WithTTL sets how long sessions are kept before eviction.
func WithTTL(d time.Duration) StoreOption {
	return func(s *InMemoryStore) { s.ttl = d }
}

// WithClock injects a time source for testing.
func WithClock(now func() time.Time) StoreOption {
	return func(s *InMemoryStore) { s.now = now }
}

// NewInMemoryStore creates a bounded, TTL-evicting session store.
// Defaults: maxSize=1000, TTL=1 hour.
func NewInMemoryStore(opts ...StoreOption) *InMemoryStore {
	s := &InMemoryStore{
		sessions: make(map[string]timedSession),
		maxSize:  1000,
		ttl:      time.Hour,
		now:      time.Now,
	}
	for _, opt := range opts {
		opt(s)
	}
	return s
}

func (s *InMemoryStore) Put(session Session) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.evictExpiredLocked()

	// If at capacity after eviction, drop the oldest entry
	if len(s.sessions) >= s.maxSize {
		s.evictOldestLocked()
	}

	s.sessions[session.SessionID] = timedSession{session: session, storedAt: s.now()}
}

func (s *InMemoryStore) Get(sessionID string) (Session, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	ts, ok := s.sessions[sessionID]
	if !ok {
		return Session{}, false
	}
	if s.now().Sub(ts.storedAt) > s.ttl {
		return Session{}, false
	}
	return ts.session, true
}

// FindFirst returns the first non-expired session matching the predicate.
func (s *InMemoryStore) FindFirst(pred func(Session) bool) (Session, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	now := s.now()
	for _, ts := range s.sessions {
		if now.Sub(ts.storedAt) <= s.ttl && pred(ts.session) {
			return ts.session, true
		}
	}
	return Session{}, false
}

// evictExpiredLocked removes expired entries. Caller must hold write lock.
func (s *InMemoryStore) evictExpiredLocked() {
	now := s.now()
	for id, ts := range s.sessions {
		if now.Sub(ts.storedAt) > s.ttl {
			delete(s.sessions, id)
		}
	}
}

// evictOldestLocked removes the oldest entry. Caller must hold write lock.
func (s *InMemoryStore) evictOldestLocked() {
	var oldestID string
	var oldestTime time.Time
	for id, ts := range s.sessions {
		if oldestID == "" || ts.storedAt.Before(oldestTime) {
			oldestID = id
			oldestTime = ts.storedAt
		}
	}
	if oldestID != "" {
		delete(s.sessions, oldestID)
	}
}
