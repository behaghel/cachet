package statuslist

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
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

// ListInfo holds summary statistics for a status list.
type ListInfo struct {
	ID        string `json:"id"`
	Purpose   string `json:"purpose"`
	Allocated int    `json:"allocated"`
	Revoked   int    `json:"revoked"`
	Capacity  int    `json:"capacity"`
}

// Store manages multiple status lists.
type Store struct {
	mu         sync.RWMutex
	lists      map[string]*StatusList
	persistDir string // directory for file persistence (empty = no persistence)
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

// NewPersistentStore creates a store that persists to disk on every mutation.
// Loads existing state from persistDir if available.
func NewPersistentStore(persistDir string) (*Store, error) {
	s := NewStore()
	s.persistDir = persistDir

	if err := s.loadFromDisk(); err != nil {
		return nil, fmt.Errorf("loading persisted state: %w", err)
	}
	return s, nil
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
	// Best-effort persist — don't fail the allocation on disk errors
	_ = s.saveToDisk()
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
	if err := list.bitstring.SetBit(index); err != nil {
		return err
	}
	_ = s.saveToDisk()
	return nil
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

// Info returns summary statistics for a status list.
func (s *Store) Info(listID string) (ListInfo, error) {
	s.mu.RLock()
	list, ok := s.lists[listID]
	s.mu.RUnlock()
	if !ok {
		return ListInfo{}, fmt.Errorf("status list %q not found", listID)
	}

	list.mu.Lock()
	defer list.mu.Unlock()

	revoked := countSetBits(list.bitstring.bits)
	return ListInfo{
		ID:        list.ID,
		Purpose:   list.Purpose,
		Allocated: list.nextIndex,
		Revoked:   revoked,
		Capacity:  len(list.bitstring.bits) * 8,
	}, nil
}

// countSetBits counts the number of 1-bits in a byte slice.
func countSetBits(data []byte) int {
	count := 0
	for _, b := range data {
		for b != 0 {
			count += int(b & 1)
			b >>= 1
		}
	}
	return count
}

// persistState is the JSON-serializable form of the store.
type persistState struct {
	Lists []persistList `json:"lists"`
}

type persistList struct {
	ID        string `json:"id"`
	Purpose   string `json:"purpose"`
	NextIndex int    `json:"nextIndex"`
	Encoded   string `json:"encoded"` // base64url(gzip(bitstring))
}

// saveToDisk persists the current state. No-op if persistDir is empty.
func (s *Store) saveToDisk() error {
	if s.persistDir == "" {
		return nil
	}

	s.mu.RLock()
	state := persistState{Lists: make([]persistList, 0, len(s.lists))}
	for _, list := range s.lists {
		encoded, err := list.bitstring.Encode()
		if err != nil {
			s.mu.RUnlock()
			return fmt.Errorf("encoding list %s: %w", list.ID, err)
		}
		state.Lists = append(state.Lists, persistList{
			ID:        list.ID,
			Purpose:   list.Purpose,
			NextIndex: list.nextIndex,
			Encoded:   encoded,
		})
	}
	s.mu.RUnlock()

	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return fmt.Errorf("marshaling state: %w", err)
	}

	if err := os.MkdirAll(s.persistDir, 0o750); err != nil {
		return fmt.Errorf("creating persist dir: %w", err)
	}

	target := filepath.Join(s.persistDir, "statuslists.json")
	tmp := target + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return fmt.Errorf("writing temp file: %w", err)
	}
	if err := os.Rename(tmp, target); err != nil {
		_ = os.Remove(tmp)
		return fmt.Errorf("renaming: %w", err)
	}
	return nil
}

// loadFromDisk loads persisted state. No-op if file doesn't exist.
func (s *Store) loadFromDisk() error {
	if s.persistDir == "" {
		return nil
	}

	data, err := os.ReadFile(filepath.Join(s.persistDir, "statuslists.json"))
	if os.IsNotExist(err) {
		return nil // first run, no persisted state
	}
	if err != nil {
		return err
	}

	var state persistState
	if err := json.Unmarshal(data, &state); err != nil {
		return fmt.Errorf("parsing persisted state: %w", err)
	}

	for _, pl := range state.Lists {
		bs := NewBitstring(DefaultSize)
		if err := bs.Decode(pl.Encoded); err != nil {
			return fmt.Errorf("decoding list %s: %w", pl.ID, err)
		}
		s.lists[pl.ID] = &StatusList{
			ID:        pl.ID,
			Purpose:   pl.Purpose,
			bitstring: bs,
			nextIndex: pl.NextIndex,
		}
	}
	return nil
}
