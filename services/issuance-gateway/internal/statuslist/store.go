package statuslist

import (
	"crypto/rand"
	"encoding/json"
	"fmt"
	"math/big"
	"os"
	"path/filepath"
	"sync"
)

// ASLConfig holds EUDI Attestation Status List configuration.
type ASLConfig struct {
	MinAnonymitySet   int // minimum allocated entries before list is servable
	InitialDecoyCount int // decoy entries seeded on list creation
}

// DefaultASLConfig returns safe defaults for ASL privacy requirements.
func DefaultASLConfig() ASLConfig {
	return ASLConfig{
		MinAnonymitySet:   1000,
		InitialDecoyCount: 1000,
	}
}

// StatusList holds a bitstring and tracks index allocation for one list.
type StatusList struct {
	ID        string
	Purpose   string // "revocation" or "suspension"
	bitstring *Bitstring
	allocated *Bitstring // tracks which indices are assigned (bit=1 means taken)
	decoys    *Bitstring // tracks which indices are decoy entries
	mu        sync.Mutex
}

// ListInfo holds summary statistics for a status list.
type ListInfo struct {
	ID        string `json:"id"`
	Purpose   string `json:"purpose"`
	Allocated int    `json:"allocated"`
	Decoys    int    `json:"decoys"`
	Revoked   int    `json:"revoked"`
	Capacity  int    `json:"capacity"`
}

// Store manages multiple status lists.
type Store struct {
	mu         sync.RWMutex
	lists      map[string]*StatusList
	persistDir string // directory for file persistence (empty = no persistence)
	config     ASLConfig
}

// NewStore creates a store with a default "1" revocation list and ASL decoy seeding.
func NewStore() *Store {
	return NewStoreWithConfig(DefaultASLConfig())
}

// NewStoreWithConfig creates a store with a default "1" revocation list using the given config.
func NewStoreWithConfig(config ASLConfig) *Store {
	s := &Store{lists: make(map[string]*StatusList), config: config}
	list := &StatusList{
		ID:        "1",
		Purpose:   "revocation",
		bitstring: NewBitstring(DefaultSize),
		allocated: NewBitstring(DefaultSize),
		decoys:    NewBitstring(DefaultSize),
	}
	s.lists["1"] = list
	// Seed decoys for herd privacy
	if config.InitialDecoyCount > 0 {
		_ = list.seedDecoys(config.InitialDecoyCount)
	}
	return s
}

// NewPersistentStore creates a store that persists to disk on every mutation.
// Loads existing state from persistDir if available.
func NewPersistentStore(persistDir string) (*Store, error) {
	return NewPersistentStoreWithConfig(persistDir, DefaultASLConfig())
}

// NewPersistentStoreWithConfig creates a persistent store with the given ASL config.
func NewPersistentStoreWithConfig(persistDir string, config ASLConfig) (*Store, error) {
	s := NewStoreWithConfig(config)
	s.persistDir = persistDir

	if err := s.loadFromDisk(); err != nil {
		return nil, fmt.Errorf("loading persisted state: %w", err)
	}
	return s, nil
}

// AllocateIndex assigns a random free index in a list and returns it.
// Uses crypto/rand for CSPRNG-based random index selection (EUDI ASL requirement).
func (s *Store) AllocateIndex(listID string) (int, error) {
	s.mu.RLock()
	list, ok := s.lists[listID]
	s.mu.RUnlock()
	if !ok {
		return 0, fmt.Errorf("status list %q not found", listID)
	}

	list.mu.Lock()
	defer list.mu.Unlock()

	capacity := list.allocated.Capacity()
	allocatedCount := list.allocated.CountSetBits()
	if allocatedCount >= capacity {
		return 0, fmt.Errorf("status list %q is full", listID)
	}

	// Rejection sampling with crypto/rand
	const maxRetries = 100
	for i := 0; i < maxRetries; i++ {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(capacity)))
		if err != nil {
			return 0, fmt.Errorf("crypto/rand: %w", err)
		}
		idx := int(n.Int64())
		if !list.allocated.IsSet(idx) {
			_ = list.allocated.SetBit(idx)
			_ = s.saveToDisk()
			return idx, nil
		}
	}

	// Fallback: linear scan for a free index (should be extremely rare)
	for idx := 0; idx < capacity; idx++ {
		if !list.allocated.IsSet(idx) {
			_ = list.allocated.SetBit(idx)
			_ = s.saveToDisk()
			return idx, nil
		}
	}

	return 0, fmt.Errorf("status list %q is full", listID)
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
// Returns ErrAnonymitySetNotMet if the list has fewer entries than MinAnonymitySet.
func (s *Store) GetEncoded(listID string) (string, error) {
	s.mu.RLock()
	list, ok := s.lists[listID]
	s.mu.RUnlock()
	if !ok {
		return "", fmt.Errorf("status list %q not found", listID)
	}

	allocatedCount := list.allocated.CountSetBits()
	if allocatedCount < s.config.MinAnonymitySet {
		return "", ErrAnonymitySetNotMet
	}

	return list.bitstring.Encode()
}

// ErrAnonymitySetNotMet is returned when a status list has too few entries for herd privacy.
var ErrAnonymitySetNotMet = fmt.Errorf("anonymity set not met: status list has too few entries")

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

	return ListInfo{
		ID:        list.ID,
		Purpose:   list.Purpose,
		Allocated: list.allocated.CountSetBits(),
		Decoys:    list.decoys.CountSetBits(),
		Revoked:   list.bitstring.CountSetBits(),
		Capacity:  list.bitstring.Capacity(),
	}, nil
}

// seedDecoys allocates count random indices as decoy entries.
// Decoys are marked in both allocated and decoys bitmaps, but NOT in bitstring
// (so revocation count stays accurate).
func (list *StatusList) seedDecoys(count int) error {
	capacity := list.allocated.Capacity()
	seeded := 0
	const maxAttempts = 1000000 // safety bound
	for attempts := 0; seeded < count && attempts < maxAttempts; attempts++ {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(capacity)))
		if err != nil {
			return fmt.Errorf("crypto/rand: %w", err)
		}
		idx := int(n.Int64())
		if !list.allocated.IsSet(idx) {
			_ = list.allocated.SetBit(idx)
			_ = list.decoys.SetBit(idx)
			seeded++
		}
	}
	if seeded < count {
		return fmt.Errorf("could only seed %d of %d decoys", seeded, count)
	}
	return nil
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
	ID               string `json:"id"`
	Purpose          string `json:"purpose"`
	NextIndex        int    `json:"nextIndex,omitempty"`        // v1 compat: retained for migration
	Encoded          string `json:"encoded"`                    // base64url(gzip(bitstring))
	AllocatedEncoded string `json:"allocatedEncoded,omitempty"` // v2: allocation bitmap
	DecoyEncoded     string `json:"decoyEncoded,omitempty"`     // v2: decoy bitmap
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
		allocEncoded, err := list.allocated.Encode()
		if err != nil {
			s.mu.RUnlock()
			return fmt.Errorf("encoding allocated for list %s: %w", list.ID, err)
		}
		decoyEncoded, err := list.decoys.Encode()
		if err != nil {
			s.mu.RUnlock()
			return fmt.Errorf("encoding decoys for list %s: %w", list.ID, err)
		}
		state.Lists = append(state.Lists, persistList{
			ID:               list.ID,
			Purpose:          list.Purpose,
			Encoded:          encoded,
			AllocatedEncoded: allocEncoded,
			DecoyEncoded:     decoyEncoded,
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
// Handles migration from v1 (nextIndex) to v2 (allocated/decoy bitmaps).
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

		allocated := NewBitstring(DefaultSize)
		decoys := NewBitstring(DefaultSize)

		if pl.AllocatedEncoded != "" {
			// v2 format: load allocated and decoy bitmaps
			if err := allocated.Decode(pl.AllocatedEncoded); err != nil {
				return fmt.Errorf("decoding allocated for list %s: %w", pl.ID, err)
			}
			if pl.DecoyEncoded != "" {
				if err := decoys.Decode(pl.DecoyEncoded); err != nil {
					return fmt.Errorf("decoding decoys for list %s: %w", pl.ID, err)
				}
			}
		} else if pl.NextIndex > 0 {
			// v1 migration: reconstruct allocated bitmap from sequential nextIndex
			for i := 0; i < pl.NextIndex; i++ {
				_ = allocated.SetBit(i)
			}
		}

		s.lists[pl.ID] = &StatusList{
			ID:        pl.ID,
			Purpose:   pl.Purpose,
			bitstring: bs,
			allocated: allocated,
			decoys:    decoys,
		}
	}
	return nil
}
