package pack

import (
	"encoding/json"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sync"

	"github.com/cachet-id/cachet/generated/go/models"
)

// Store holds loaded pack definitions indexed by ID.
// Thread-safe: reads and writes are protected by a RWMutex.
type Store struct {
	mu         sync.RWMutex
	packs      map[string]models.PackDefinition
	disabled   map[string]bool // packs that are disabled
	embeddedFS fs.FS           // read-only baseline
	overlayDir string          // writable overlay (empty = no overlay)
}

// LoadFromFS loads all .json files from the given filesystem into the store.
func LoadFromFS(fsys fs.FS) (*Store, error) {
	store := &Store{
		packs:      make(map[string]models.PackDefinition),
		disabled:   make(map[string]bool),
		embeddedFS: fsys,
	}
	if err := store.loadFS(fsys); err != nil {
		return nil, err
	}
	return store, nil
}

// LoadWithOverlay loads embedded packs and overlays writable packs from a directory.
// Overlay packs shadow embedded packs with the same ID.
func LoadWithOverlay(embedded fs.FS, overlayDir string) (*Store, error) {
	store := &Store{
		packs:      make(map[string]models.PackDefinition),
		disabled:   make(map[string]bool),
		embeddedFS: embedded,
		overlayDir: overlayDir,
	}

	// Load embedded first
	if err := store.loadFS(embedded); err != nil {
		return nil, fmt.Errorf("loading embedded packs: %w", err)
	}

	// Overlay on top (shadows embedded)
	if overlayDir != "" {
		if err := store.loadOverlay(); err != nil {
			return nil, fmt.Errorf("loading overlay packs: %w", err)
		}
	}

	return store, nil
}

// Reload re-reads embedded + overlay packs, atomically swapping the map.
func (s *Store) Reload() error {
	newPacks := make(map[string]models.PackDefinition)

	// Reload embedded
	if s.embeddedFS != nil {
		if err := loadFSInto(s.embeddedFS, newPacks); err != nil {
			return fmt.Errorf("reloading embedded packs: %w", err)
		}
	}

	// Reload overlay
	if s.overlayDir != "" {
		if err := loadDirInto(s.overlayDir, newPacks); err != nil {
			return fmt.Errorf("reloading overlay packs: %w", err)
		}
	}

	s.mu.Lock()
	s.packs = newPacks
	s.mu.Unlock()
	return nil
}

// Put writes a pack to the overlay directory and updates the in-memory store.
// Returns an error if no overlay directory is configured.
func (s *Store) Put(p models.PackDefinition) error {
	if s.overlayDir == "" {
		return fmt.Errorf("no overlay directory configured")
	}

	data, err := json.MarshalIndent(p, "", "  ")
	if err != nil {
		return fmt.Errorf("marshaling pack: %w", err)
	}

	// Atomic write: temp file + rename
	if err := os.MkdirAll(s.overlayDir, 0o750); err != nil {
		return fmt.Errorf("creating overlay dir: %w", err)
	}
	target := filepath.Join(s.overlayDir, sanitizeFilename(p.Id)+".json")
	tmp := target + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return fmt.Errorf("writing temp file: %w", err)
	}
	if err := os.Rename(tmp, target); err != nil {
		_ = os.Remove(tmp)
		return fmt.Errorf("renaming temp file: %w", err)
	}

	s.mu.Lock()
	s.packs[p.Id] = p
	s.mu.Unlock()
	return nil
}

// SetEnabled enables or disables a pack. Disabled packs are excluded from List/ListSummary
// but remain in Get for admin inspection.
func (s *Store) SetEnabled(id string, enabled bool) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.packs[id]; !ok {
		return false
	}
	if enabled {
		delete(s.disabled, id)
	} else {
		s.disabled[id] = true
	}
	return true
}

// IsEnabled returns whether a pack is enabled.
func (s *Store) IsEnabled(id string) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return !s.disabled[id]
}

// Get returns a pack definition by ID, or false if not found.
func (s *Store) Get(id string) (models.PackDefinition, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	p, ok := s.packs[id]
	return p, ok
}

// List returns all enabled pack definitions.
func (s *Store) List() []models.PackDefinition {
	s.mu.RLock()
	defer s.mu.RUnlock()
	result := make([]models.PackDefinition, 0, len(s.packs))
	for _, p := range s.packs {
		if !s.disabled[p.Id] {
			result = append(result, p)
		}
	}
	return result
}

// ListAll returns all pack definitions including disabled ones.
func (s *Store) ListAll() []models.PackDefinition {
	s.mu.RLock()
	defer s.mu.RUnlock()
	result := make([]models.PackDefinition, 0, len(s.packs))
	for _, p := range s.packs {
		result = append(result, p)
	}
	return result
}

// ListSummary returns pack summaries (id, version, name only) for enabled packs.
func (s *Store) ListSummary() []models.Pack {
	s.mu.RLock()
	defer s.mu.RUnlock()
	result := make([]models.Pack, 0, len(s.packs))
	for _, p := range s.packs {
		if !s.disabled[p.Id] {
			result = append(result, models.Pack{
				Id:      p.Id,
				Version: p.Version,
				Name:    p.Name,
			})
		}
	}
	return result
}

// loadFS loads packs from an fs.FS into the store's map (not thread-safe, called during init).
func (s *Store) loadFS(fsys fs.FS) error {
	return loadFSInto(fsys, s.packs)
}

func loadFSInto(fsys fs.FS, target map[string]models.PackDefinition) error {
	entries, err := fs.ReadDir(fsys, ".")
	if err != nil {
		return fmt.Errorf("reading pack directory: %w", err)
	}

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if len(name) < 6 || name[len(name)-5:] != ".json" {
			continue
		}

		data, err := fs.ReadFile(fsys, name)
		if err != nil {
			return fmt.Errorf("reading %s: %w", name, err)
		}

		var p models.PackDefinition
		if err := json.Unmarshal(data, &p); err != nil {
			return fmt.Errorf("parsing %s: %w", name, err)
		}

		target[p.Id] = p
	}
	return nil
}

func (s *Store) loadOverlay() error {
	return loadDirInto(s.overlayDir, s.packs)
}

func loadDirInto(dir string, target map[string]models.PackDefinition) error {
	entries, err := os.ReadDir(dir)
	if os.IsNotExist(err) {
		return nil // overlay dir doesn't exist yet — that's fine
	}
	if err != nil {
		return fmt.Errorf("reading overlay dir: %w", err)
	}

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if len(name) < 6 || name[len(name)-5:] != ".json" {
			continue
		}

		data, err := os.ReadFile(filepath.Clean(filepath.Join(dir, name)))
		if err != nil {
			return fmt.Errorf("reading %s: %w", name, err)
		}

		var p models.PackDefinition
		if err := json.Unmarshal(data, &p); err != nil {
			return fmt.Errorf("parsing %s: %w", name, err)
		}

		target[p.Id] = p
	}
	return nil
}

// sanitizeFilename replaces dots with dashes for safe filenames.
func sanitizeFilename(id string) string {
	result := make([]byte, len(id))
	for i := range id {
		if id[i] == '/' || id[i] == '\\' || id[i] == ' ' {
			result[i] = '-'
		} else {
			result[i] = id[i]
		}
	}
	return string(result)
}
