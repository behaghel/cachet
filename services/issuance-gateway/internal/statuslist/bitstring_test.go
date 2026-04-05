package statuslist

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

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

func TestStoreAllocateIndex(t *testing.T) {
	s := NewStore()

	idx0, err := s.AllocateIndex("1")
	require.NoError(t, err)
	assert.Equal(t, 0, idx0)

	idx1, err := s.AllocateIndex("1")
	require.NoError(t, err)
	assert.Equal(t, 1, idx1)

	idx2, err := s.AllocateIndex("1")
	require.NoError(t, err)
	assert.Equal(t, 2, idx2)
}

func TestStoreRevoke(t *testing.T) {
	s := NewStore()

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
	s := NewStore()

	_, err := s.AllocateIndex("nonexistent")
	assert.Error(t, err)

	err = s.Revoke("nonexistent", 0)
	assert.Error(t, err)

	_, err = s.GetEncoded("nonexistent")
	assert.Error(t, err)
}
