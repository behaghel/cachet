package nonce

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestIssue_ReturnsUniqueNonces(t *testing.T) {
	s := NewStore()
	n1, _ := s.Issue()
	n2, _ := s.Issue()
	assert.NotEqual(t, n1, n2)
	assert.Len(t, n1, 22) // 16 bytes base64url = 22 chars
}

func TestIssue_ReturnsTTLSeconds(t *testing.T) {
	s := NewStore(WithTTL(3 * time.Minute))
	_, ttl := s.Issue()
	assert.Equal(t, 180, ttl)
}

func TestConsume_ValidNonce(t *testing.T) {
	s := NewStore()
	nonce, _ := s.Issue()

	ok := s.Consume(nonce)
	assert.True(t, ok)
}

func TestConsume_SingleUse(t *testing.T) {
	s := NewStore()
	nonce, _ := s.Issue()

	ok1 := s.Consume(nonce)
	ok2 := s.Consume(nonce)

	assert.True(t, ok1, "first use should succeed")
	assert.False(t, ok2, "second use must fail — single-use nonce")
}

func TestConsume_UnknownNonce(t *testing.T) {
	s := NewStore()
	ok := s.Consume("never-issued")
	assert.False(t, ok)
}

func TestConsume_ExpiredNonce(t *testing.T) {
	now := time.Now()
	s := NewStore(
		WithTTL(1*time.Minute),
		WithClock(func() time.Time { return now }),
	)

	nonce, _ := s.Issue()

	// Advance clock past TTL
	s.now = func() time.Time { return now.Add(2 * time.Minute) }

	ok := s.Consume(nonce)
	assert.False(t, ok, "expired nonce must be rejected")
}

func TestEviction_MaxSize(t *testing.T) {
	s := NewStore(WithMaxSize(2))

	n1, _ := s.Issue()
	_, _ = s.Issue()
	_, _ = s.Issue() // should evict n1

	ok := s.Consume(n1)
	assert.False(t, ok, "evicted nonce should not be consumable")
}

func TestEviction_ExpiredEntriesCleanedOnIssue(t *testing.T) {
	now := time.Now()
	s := NewStore(
		WithTTL(1*time.Minute),
		WithClock(func() time.Time { return now }),
	)

	_, _ = s.Issue()
	_, _ = s.Issue()

	// Advance clock past TTL
	s.now = func() time.Time { return now.Add(2 * time.Minute) }

	// Issue should evict expired entries
	n3, _ := s.Issue()

	s.mu.Lock()
	count := len(s.nonces)
	s.mu.Unlock()

	assert.Equal(t, 1, count, "expired nonces should be evicted")

	ok := s.Consume(n3)
	require.True(t, ok, "fresh nonce should be valid")
}

func TestGenerateNonce_Length(t *testing.T) {
	n := generateNonce()
	assert.Len(t, n, 22) // 128 bits = 16 bytes → 22 base64url chars (no padding)
}
