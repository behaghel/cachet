package veriff

import "sync"

// SessionStore stores verified Veriff sessions.
type SessionStore interface {
	Put(session Session)
	Get(sessionID string) (Session, bool)
}

// InMemoryStore is a thread-safe in-memory session store.
type InMemoryStore struct {
	mu       sync.RWMutex
	sessions map[string]Session
}

func NewInMemoryStore() *InMemoryStore {
	return &InMemoryStore{sessions: make(map[string]Session)}
}

func (s *InMemoryStore) Put(session Session) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.sessions[session.SessionID] = session
}

func (s *InMemoryStore) Get(sessionID string) (Session, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	sess, ok := s.sessions[sessionID]
	return sess, ok
}

// FindFirst returns the first session matching the predicate.
func (s *InMemoryStore) FindFirst(pred func(Session) bool) (Session, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	for _, sess := range s.sessions {
		if pred(sess) {
			return sess, true
		}
	}
	return Session{}, false
}
