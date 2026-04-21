package statuslist

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- Bitstring unit tests ---

func TestSetGetBit(t *testing.T) {
	b := NewBitstring(16) // 128 bits

	// Initially all bits are 0
	revoked, err := b.GetBit(0)
	require.NoError(t, err)
	assert.False(t, revoked)

	// Set bit 42
	require.NoError(t, b.SetBit(42))
	revoked, err = b.GetBit(42)
	require.NoError(t, err)
	assert.True(t, revoked)

	// Adjacent bits unchanged
	revoked, err = b.GetBit(41)
	require.NoError(t, err)
	assert.False(t, revoked)
	revoked, err = b.GetBit(43)
	require.NoError(t, err)
	assert.False(t, revoked)
}

func TestBitBoundaries(t *testing.T) {
	b := NewBitstring(2) // 16 bits

	// First bit
	require.NoError(t, b.SetBit(0))
	revoked, _ := b.GetBit(0)
	assert.True(t, revoked)

	// Last bit
	require.NoError(t, b.SetBit(15))
	revoked, _ = b.GetBit(15)
	assert.True(t, revoked)

	// Out of range
	assert.Error(t, b.SetBit(16))
	_, err := b.GetBit(16)
	assert.Error(t, err)
}

func TestByteBoundary(t *testing.T) {
	b := NewBitstring(2)

	// Set bits at byte boundary (bit 7 and bit 8)
	require.NoError(t, b.SetBit(7))
	require.NoError(t, b.SetBit(8))

	r7, _ := b.GetBit(7)
	r8, _ := b.GetBit(8)
	assert.True(t, r7)
	assert.True(t, r8)

	// Bit 6 and 9 should be unset
	r6, _ := b.GetBit(6)
	r9, _ := b.GetBit(9)
	assert.False(t, r6)
	assert.False(t, r9)
}

func TestEncodeDecodeRoundtrip(t *testing.T) {
	b := NewBitstring(DefaultSize)

	// Set some bits
	require.NoError(t, b.SetBit(0))
	require.NoError(t, b.SetBit(42))
	require.NoError(t, b.SetBit(1000))
	require.NoError(t, b.SetBit(131071)) // last bit in 16KB

	encoded, err := b.Encode()
	require.NoError(t, err)
	assert.NotEmpty(t, encoded)

	// Decode into a new bitstring
	b2 := NewBitstring(0)
	require.NoError(t, b2.Decode(encoded))

	// Verify bits match
	for _, idx := range []int{0, 42, 1000, 131071} {
		revoked, err := b2.GetBit(idx)
		require.NoError(t, err)
		assert.True(t, revoked, "bit %d should be set", idx)
	}

	// Verify unset bit
	revoked, err := b2.GetBit(1)
	require.NoError(t, err)
	assert.False(t, revoked)
}

func TestIsSet(t *testing.T) {
	b := NewBitstring(16)
	assert.False(t, b.IsSet(0))
	require.NoError(t, b.SetBit(42))
	assert.True(t, b.IsSet(42))
	assert.False(t, b.IsSet(43))
	// Out of range returns false
	assert.False(t, b.IsSet(200))
}

func TestCountSetBits(t *testing.T) {
	b := NewBitstring(16)
	assert.Equal(t, 0, b.CountSetBits())
	require.NoError(t, b.SetBit(0))
	require.NoError(t, b.SetBit(42))
	require.NoError(t, b.SetBit(100))
	assert.Equal(t, 3, b.CountSetBits())
}

func TestCapacity(t *testing.T) {
	b := NewBitstring(16)
	assert.Equal(t, 128, b.Capacity())
	b2 := NewBitstring(DefaultSize)
	assert.Equal(t, 131072, b2.Capacity())
}

// --- Store: random allocation tests ---

// noDecoyConfig returns an ASL config with no decoys for deterministic testing.
func noDecoyConfig() ASLConfig {
	return ASLConfig{MinAnonymitySet: 0, InitialDecoyCount: 0}
}

func TestAllocateIndex_Random(t *testing.T) {
	s := NewStoreWithConfig(noDecoyConfig())

	indices := make(map[int]bool)
	for i := 0; i < 100; i++ {
		idx, err := s.AllocateIndex("1")
		require.NoError(t, err)
		indices[idx] = true
	}

	// All 100 indices must be unique
	assert.Len(t, indices, 100)

	// Verify randomness: not all sequential (check no run of 100 consecutive)
	sequential := true
	for i := 0; i < 100; i++ {
		if !indices[i] {
			sequential = false
			break
		}
	}
	assert.False(t, sequential, "indices should not be perfectly sequential 0..99")
}

func TestAllocateIndex_NoDuplicates(t *testing.T) {
	s := NewStoreWithConfig(noDecoyConfig())

	seen := make(map[int]bool)
	for i := 0; i < 500; i++ {
		idx, err := s.AllocateIndex("1")
		require.NoError(t, err)
		assert.False(t, seen[idx], "duplicate index %d on allocation %d", idx, i)
		seen[idx] = true
	}
}

func TestAllocateIndex_FullList(t *testing.T) {
	cfg := ASLConfig{MinAnonymitySet: 0, InitialDecoyCount: 0}
	s := &Store{lists: make(map[string]*StatusList), config: cfg}
	// Tiny list: 16 bytes = 128 slots
	s.lists["1"] = &StatusList{
		ID:        "1",
		Purpose:   "revocation",
		bitstring: NewBitstring(16),
		allocated: NewBitstring(16),
		decoys:    NewBitstring(16),
	}

	for i := 0; i < 128; i++ {
		_, err := s.AllocateIndex("1")
		require.NoError(t, err, "allocation %d should succeed", i)
	}

	// 129th should fail
	_, err := s.AllocateIndex("1")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "full")
}

func TestAllocateIndex_AllocatedBitmapTracking(t *testing.T) {
	s := NewStoreWithConfig(noDecoyConfig())

	idx, err := s.AllocateIndex("1")
	require.NoError(t, err)

	// The allocated bitmap should have this bit set
	s.mu.RLock()
	list := s.lists["1"]
	s.mu.RUnlock()
	assert.True(t, list.allocated.IsSet(idx))
}

// --- Store: decoy tests ---

func TestDecoys_SeedOnCreation(t *testing.T) {
	cfg := ASLConfig{MinAnonymitySet: 0, InitialDecoyCount: 100}
	s := NewStoreWithConfig(cfg)

	s.mu.RLock()
	list := s.lists["1"]
	s.mu.RUnlock()

	assert.Equal(t, 100, list.decoys.CountSetBits())
	assert.Equal(t, 100, list.allocated.CountSetBits())
	// Revocation bitstring should be clean (decoys are not revoked)
	assert.Equal(t, 0, list.bitstring.CountSetBits())
}

func TestDecoys_NotAllocatedToCredentials(t *testing.T) {
	cfg := ASLConfig{MinAnonymitySet: 0, InitialDecoyCount: 50}
	s := NewStoreWithConfig(cfg)

	s.mu.RLock()
	list := s.lists["1"]
	s.mu.RUnlock()

	// Allocate 50 real indices
	for i := 0; i < 50; i++ {
		idx, err := s.AllocateIndex("1")
		require.NoError(t, err)
		// Real allocations must NOT overlap with decoy indices
		assert.False(t, list.decoys.IsSet(idx), "index %d is a decoy but was allocated to a credential", idx)
	}

	// 50 decoys + 50 real = 100 total allocated
	assert.Equal(t, 100, list.allocated.CountSetBits())
	assert.Equal(t, 50, list.decoys.CountSetBits())
}

func TestInfo_IncludesDecoys(t *testing.T) {
	cfg := ASLConfig{MinAnonymitySet: 0, InitialDecoyCount: 200}
	s := NewStoreWithConfig(cfg)

	// Allocate 3 real credentials
	for i := 0; i < 3; i++ {
		_, err := s.AllocateIndex("1")
		require.NoError(t, err)
	}

	info, err := s.Info("1")
	require.NoError(t, err)
	assert.Equal(t, 203, info.Allocated) // 200 decoys + 3 real
	assert.Equal(t, 200, info.Decoys)
	assert.Equal(t, 0, info.Revoked)
	assert.Equal(t, 131072, info.Capacity)
}

// --- Store: revoke still works ---

func TestStoreRevoke(t *testing.T) {
	s := NewStoreWithConfig(noDecoyConfig())

	idx, _ := s.AllocateIndex("1")
	require.NoError(t, s.Revoke("1", idx))

	encoded, err := s.GetEncoded("1")
	require.NoError(t, err)

	// Decode and check
	b := NewBitstring(0)
	require.NoError(t, b.Decode(encoded))
	revoked, _ := b.GetBit(idx)
	assert.True(t, revoked)
}

func TestStoreNotFound(t *testing.T) {
	s := NewStoreWithConfig(noDecoyConfig())

	_, err := s.AllocateIndex("nonexistent")
	assert.Error(t, err)

	err = s.Revoke("nonexistent", 0)
	assert.Error(t, err)

	_, err = s.GetEncoded("nonexistent")
	assert.Error(t, err)
}

// --- Persistence tests ---

func TestPersistence_RoundtripV2(t *testing.T) {
	dir := t.TempDir()
	cfg := ASLConfig{MinAnonymitySet: 0, InitialDecoyCount: 10}

	// Create store, allocate some indices, revoke one
	s1, err := NewPersistentStoreWithConfig(dir, cfg)
	require.NoError(t, err)

	var allocated []int
	for i := 0; i < 5; i++ {
		idx, err := s1.AllocateIndex("1")
		require.NoError(t, err)
		allocated = append(allocated, idx)
	}
	require.NoError(t, s1.Revoke("1", allocated[0]))

	// Load into new store
	s2, err := NewPersistentStoreWithConfig(dir, cfg)
	require.NoError(t, err)

	info, err := s2.Info("1")
	require.NoError(t, err)
	assert.Equal(t, 15, info.Allocated) // 10 decoys + 5 real
	assert.Equal(t, 10, info.Decoys)
	assert.Equal(t, 1, info.Revoked)

	// Allocating again should not conflict with previously allocated indices
	s2.mu.RLock()
	list := s2.lists["1"]
	s2.mu.RUnlock()
	for _, idx := range allocated {
		assert.True(t, list.allocated.IsSet(idx), "index %d should be allocated after reload", idx)
	}
}

func TestPersistence_MigrationFromNextIndex(t *testing.T) {
	dir := t.TempDir()

	// Write a v1 persisted file (with nextIndex, no allocatedEncoded)
	bs := NewBitstring(DefaultSize)
	_ = bs.SetBit(0) // revoke index 0
	encoded, err := bs.Encode()
	require.NoError(t, err)

	v1State := persistState{
		Lists: []persistList{{
			ID:        "1",
			Purpose:   "revocation",
			NextIndex: 42,
			Encoded:   encoded,
		}},
	}
	data, err := json.MarshalIndent(v1State, "", "  ")
	require.NoError(t, err)
	require.NoError(t, os.WriteFile(filepath.Join(dir, "statuslists.json"), data, 0o600))

	// Load with new store
	cfg := ASLConfig{MinAnonymitySet: 0, InitialDecoyCount: 0}
	s, err := NewPersistentStoreWithConfig(dir, cfg)
	require.NoError(t, err)

	// Should have reconstructed allocated bitmap: bits 0..41 set
	s.mu.RLock()
	list := s.lists["1"]
	s.mu.RUnlock()

	assert.Equal(t, 42, list.allocated.CountSetBits())
	for i := 0; i < 42; i++ {
		assert.True(t, list.allocated.IsSet(i), "bit %d should be allocated after v1 migration", i)
	}
	assert.False(t, list.allocated.IsSet(42))

	// Revocation state preserved
	assert.Equal(t, 1, list.bitstring.CountSetBits())
	revoked, _ := list.bitstring.GetBit(0)
	assert.True(t, revoked)
}

// --- Anonymity set tests ---

func TestGetEncoded_BelowAnonymitySet(t *testing.T) {
	cfg := ASLConfig{MinAnonymitySet: 100, InitialDecoyCount: 0}
	s := NewStoreWithConfig(cfg)

	// Allocate fewer than minimum
	for i := 0; i < 50; i++ {
		_, err := s.AllocateIndex("1")
		require.NoError(t, err)
	}

	_, err := s.GetEncoded("1")
	assert.ErrorIs(t, err, ErrAnonymitySetNotMet)
}

func TestGetEncoded_WithDecoys_AlwaysServable(t *testing.T) {
	cfg := ASLConfig{MinAnonymitySet: 100, InitialDecoyCount: 100}
	s := NewStoreWithConfig(cfg)

	// Should be servable immediately thanks to decoys
	encoded, err := s.GetEncoded("1")
	require.NoError(t, err)
	assert.NotEmpty(t, encoded)
}
