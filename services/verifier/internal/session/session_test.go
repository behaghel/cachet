package session

import (
	"encoding/base64"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestCreate(t *testing.T) {
	m := NewManager(5 * time.Minute)
	s := m.Create("did:web:verifier.example")

	assert.NotEmpty(t, s.ID)
	assert.NotEmpty(t, s.Nonce)
	assert.Equal(t, "did:web:verifier.example", s.VerifierDID)
	assert.False(t, s.Used)

	// Nonce should be at least 128 bits (22 base64url chars for 16 bytes)
	nonceBytes, err := base64.RawURLEncoding.DecodeString(s.Nonce)
	require.NoError(t, err)
	assert.GreaterOrEqual(t, len(nonceBytes), 16)
}

func TestConsume_Valid(t *testing.T) {
	m := NewManager(5 * time.Minute)
	s := m.Create("did:web:verifier.example")

	consumed, err := m.Consume(s.ID)
	require.NoError(t, err)
	assert.Equal(t, s.Nonce, consumed.Nonce)
	assert.True(t, consumed.Used)
}

func TestConsume_OneTimeUse(t *testing.T) {
	m := NewManager(5 * time.Minute)
	s := m.Create("did:web:verifier.example")

	_, err := m.Consume(s.ID)
	require.NoError(t, err)

	// Second consume should fail
	_, err = m.Consume(s.ID)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "already consumed")
}

func TestConsume_NotFound(t *testing.T) {
	m := NewManager(5 * time.Minute)
	_, err := m.Consume("nonexistent")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "not found")
}

func TestConsume_Expired(t *testing.T) {
	m := NewManager(1 * time.Millisecond)
	s := m.Create("did:web:verifier.example")

	time.Sleep(5 * time.Millisecond)

	_, err := m.Consume(s.ID)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "expired")
}

func TestUniqueNonces(t *testing.T) {
	m := NewManager(5 * time.Minute)
	s1 := m.Create("v1")
	s2 := m.Create("v2")
	assert.NotEqual(t, s1.Nonce, s2.Nonce, "each session should have a unique nonce")
}
