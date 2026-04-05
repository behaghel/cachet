package statuslist

import (
	"fmt"
	"sync"
)

// StatusList holds a bitstring and tracks index allocation for one list.
type StatusList struct {
	ID        string
	Purpose   string // "revocation" or "suspension"
	bitstring *Bitstring
	nextIndex int
	mu        sync.Mutex
}

// Store manages multiple status lists.
type Store struct {
	mu    sync.RWMutex
	lists map[string]*StatusList
}

// NewStore creates a store with a default "1" revocation list.
func NewStore() *Store {
	s := &Store{lists: make(map[string]*StatusList)}
	s.lists["1"] = &StatusList{
		ID:        "1",
		Purpose:   "revocation",
		bitstring: NewBitstring(DefaultSize),
		nextIndex: 0,
	}
	return s
}

// AllocateIndex assigns the next free index in a list and returns it.
func (s *Store) AllocateIndex(listID string) (int, error) {
	s.mu.RLock()
	list, ok := s.lists[listID]
	s.mu.RUnlock()
	if !ok {
		return 0, fmt.Errorf("status list %q not found", listID)
	}

	list.mu.Lock()
	defer list.mu.Unlock()

	idx := list.nextIndex
	if idx >= len(list.bitstring.bits)*8 {
		return 0, fmt.Errorf("status list %q is full", listID)
	}
	list.nextIndex++
	return idx, nil
}

// Revoke sets the bit at the given index to 1 (revoked).
func (s *Store) Revoke(listID string, index int) error {
	s.mu.RLock()
	list, ok := s.lists[listID]
	s.mu.RUnlock()
	if !ok {
		return fmt.Errorf("status list %q not found", listID)
	}
	return list.bitstring.SetBit(index)
}

// GetEncoded returns the base64url(gzip) encoded bitstring for a list.
func (s *Store) GetEncoded(listID string) (string, error) {
	s.mu.RLock()
	list, ok := s.lists[listID]
	s.mu.RUnlock()
	if !ok {
		return "", fmt.Errorf("status list %q not found", listID)
	}
	return list.bitstring.Encode()
}

// GetPurpose returns the purpose of a status list.
func (s *Store) GetPurpose(listID string) (string, error) {
	s.mu.RLock()
	list, ok := s.lists[listID]
	s.mu.RUnlock()
	if !ok {
		return "", fmt.Errorf("status list %q not found", listID)
	}
	return list.Purpose, nil
}
