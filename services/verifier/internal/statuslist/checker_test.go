package statuslist

import (
	"bytes"
	"compress/gzip"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// buildTestBitstring creates an encoded bitstring with specific bits set.
func buildTestBitstring(t *testing.T, size int, setBits []int) string {
	t.Helper()
	bits := make([]byte, size)
	for _, idx := range setBits {
		byteIdx := idx / 8
		bitIdx := 7 - (idx % 8)
		bits[byteIdx] |= 1 << bitIdx
	}
	var buf bytes.Buffer
	gz, err := gzip.NewWriterLevel(&buf, gzip.BestCompression)
	require.NoError(t, err)
	_, err = gz.Write(bits)
	require.NoError(t, err)
	require.NoError(t, gz.Close())
	return base64.RawURLEncoding.EncodeToString(buf.Bytes())
}

func mockStatusListServer(t *testing.T, encoded string) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "max-age=300")
		json.NewEncoder(w).Encode(map[string]string{
			"encodedList": encoded,
		})
	}))
}

func TestIsRevoked_True(t *testing.T) {
	encoded := buildTestBitstring(t, 16, []int{5, 10})
	srv := mockStatusListServer(t, encoded)
	defer srv.Close()

	checker := NewChecker()
	revoked, err := checker.IsRevoked(srv.URL, 5)
	require.NoError(t, err)
	assert.True(t, revoked)
}

func TestIsRevoked_False(t *testing.T) {
	encoded := buildTestBitstring(t, 16, []int{5})
	srv := mockStatusListServer(t, encoded)
	defer srv.Close()

	checker := NewChecker()
	revoked, err := checker.IsRevoked(srv.URL, 6)
	require.NoError(t, err)
	assert.False(t, revoked)
}

func TestCacheHit(t *testing.T) {
	callCount := 0
	encoded := buildTestBitstring(t, 16, []int{0})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		json.NewEncoder(w).Encode(map[string]string{"encodedList": encoded})
	}))
	defer srv.Close()

	checker := NewChecker()

	// First call fetches
	_, err := checker.IsRevoked(srv.URL, 0)
	require.NoError(t, err)
	assert.Equal(t, 1, callCount)

	// Second call uses cache
	_, err = checker.IsRevoked(srv.URL, 0)
	require.NoError(t, err)
	assert.Equal(t, 1, callCount, "should use cached bitstring")
}

func TestCacheExpiry(t *testing.T) {
	callCount := 0
	encoded := buildTestBitstring(t, 16, []int{0})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		json.NewEncoder(w).Encode(map[string]string{"encodedList": encoded})
	}))
	defer srv.Close()

	checker := &Checker{
		cache:      make(map[string]*cachedList),
		httpClient: &http.Client{Timeout: 10 * time.Second},
		ttl:        1 * time.Millisecond, // very short TTL for testing
	}

	_, err := checker.IsRevoked(srv.URL, 0)
	require.NoError(t, err)
	assert.Equal(t, 1, callCount)

	time.Sleep(5 * time.Millisecond)

	_, err = checker.IsRevoked(srv.URL, 0)
	require.NoError(t, err)
	assert.Equal(t, 2, callCount, "should re-fetch after TTL expires")
}

func TestServerDown(t *testing.T) {
	checker := NewChecker()
	_, err := checker.IsRevoked("http://localhost:1", 0)
	assert.Error(t, err)
}
