package veriff

import (
	"testing"
	"time"
)

func TestInMemoryStore_PutAndGet(t *testing.T) {
	store := NewInMemoryStore()
	session := Session{SessionID: "s1", Status: "approved"}
	store.Put(session)

	got, ok := store.Get("s1")
	if !ok {
		t.Fatal("expected session to be found")
	}
	if got.SessionID != "s1" {
		t.Errorf("SessionID = %q, want %q", got.SessionID, "s1")
	}
}

func TestInMemoryStore_TTLEviction(t *testing.T) {
	now := time.Now()
	clock := func() time.Time { return now }

	store := NewInMemoryStore(WithTTL(10*time.Minute), WithClock(clock))
	store.Put(Session{SessionID: "s1", Status: "approved"})

	// Advance past TTL
	now = now.Add(11 * time.Minute)

	_, ok := store.Get("s1")
	if ok {
		t.Error("expected session to be expired")
	}
}

func TestInMemoryStore_MaxSizeEviction(t *testing.T) {
	store := NewInMemoryStore(WithMaxSize(2))
	store.Put(Session{SessionID: "s1", Status: "approved"})
	store.Put(Session{SessionID: "s2", Status: "approved"})
	store.Put(Session{SessionID: "s3", Status: "approved"})

	// s1 should have been evicted (oldest)
	_, ok := store.Get("s1")
	if ok {
		t.Error("expected oldest session to be evicted")
	}

	// s2 and s3 should still be present
	if _, ok := store.Get("s2"); !ok {
		t.Error("expected s2 to be present")
	}
	if _, ok := store.Get("s3"); !ok {
		t.Error("expected s3 to be present")
	}
}

func TestInMemoryStore_FindFirst(t *testing.T) {
	store := NewInMemoryStore()
	store.Put(Session{SessionID: "s1", Status: "declined"})
	store.Put(Session{SessionID: "s2", Status: "approved"})

	got, ok := store.FindFirst(func(s Session) bool { return s.Status == "approved" })
	if !ok {
		t.Fatal("expected to find approved session")
	}
	if got.SessionID != "s2" {
		t.Errorf("SessionID = %q, want %q", got.SessionID, "s2")
	}
}

func TestInMemoryStore_FindFirst_SkipsExpired(t *testing.T) {
	now := time.Now()
	clock := func() time.Time { return now }
	store := NewInMemoryStore(WithTTL(5*time.Minute), WithClock(clock))

	store.Put(Session{SessionID: "old", Status: "approved"})
	now = now.Add(6 * time.Minute) // expire "old"
	store.Put(Session{SessionID: "new", Status: "approved"})

	got, ok := store.FindFirst(func(s Session) bool { return s.Status == "approved" })
	if !ok {
		t.Fatal("expected to find non-expired session")
	}
	if got.SessionID != "new" {
		t.Errorf("SessionID = %q, want %q", got.SessionID, "new")
	}
}
