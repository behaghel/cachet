package statuslist

import (
	"bytes"
	"compress/gzip"
	"encoding/base64"
	"fmt"
	"io"
	"sync"
)

const (
	// DefaultSize is 16KB = 131,072 credential slots.
	DefaultSize = 16 * 1024
)

// Bitstring is a thread-safe bit array for StatusList2021.
// Each bit represents one credential: 0 = active, 1 = revoked/suspended.
type Bitstring struct {
	mu   sync.RWMutex
	bits []byte
}

// NewBitstring creates a bitstring with the given byte size.
func NewBitstring(size int) *Bitstring {
	return &Bitstring{bits: make([]byte, size)}
}

// SetBit sets the bit at the given index to 1 (revoked).
func (b *Bitstring) SetBit(index int) error {
	b.mu.Lock()
	defer b.mu.Unlock()

	byteIdx := index / 8
	bitIdx := 7 - (index % 8) // MSB first per W3C spec
	if byteIdx >= len(b.bits) {
		return fmt.Errorf("index %d out of range (max %d)", index, len(b.bits)*8-1)
	}
	b.bits[byteIdx] |= 1 << bitIdx
	return nil
}

// GetBit returns true if the bit at the given index is 1 (revoked).
func (b *Bitstring) GetBit(index int) (bool, error) {
	b.mu.RLock()
	defer b.mu.RUnlock()

	byteIdx := index / 8
	bitIdx := 7 - (index % 8)
	if byteIdx >= len(b.bits) {
		return false, fmt.Errorf("index %d out of range (max %d)", index, len(b.bits)*8-1)
	}
	return (b.bits[byteIdx]>>bitIdx)&1 == 1, nil
}

// Encode returns base64url(gzip(bits)) per W3C Bitstring Status List.
func (b *Bitstring) Encode() (string, error) {
	b.mu.RLock()
	defer b.mu.RUnlock()

	var buf bytes.Buffer
	gz, err := gzip.NewWriterLevel(&buf, gzip.BestCompression)
	if err != nil {
		return "", fmt.Errorf("create gzip writer: %w", err)
	}
	if _, err := gz.Write(b.bits); err != nil {
		return "", fmt.Errorf("gzip write: %w", err)
	}
	if err := gz.Close(); err != nil {
		return "", fmt.Errorf("gzip close: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(buf.Bytes()), nil
}

// Decode populates the bitstring from base64url(gzip(bits)).
func (b *Bitstring) Decode(encoded string) error {
	b.mu.Lock()
	defer b.mu.Unlock()

	compressed, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		return fmt.Errorf("base64url decode: %w", err)
	}
	gz, err := gzip.NewReader(bytes.NewReader(compressed))
	if err != nil {
		return fmt.Errorf("gzip reader: %w", err)
	}
	defer gz.Close()

	decompressed, err := io.ReadAll(gz)
	if err != nil {
		return fmt.Errorf("gzip decompress: %w", err)
	}
	b.bits = decompressed
	return nil
}
