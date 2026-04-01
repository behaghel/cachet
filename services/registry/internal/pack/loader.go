package pack

import (
	"encoding/json"
	"fmt"
	"io/fs"

	"github.com/cachet-id/cachet/generated/go/models"
)

// Store holds loaded pack definitions indexed by ID.
type Store struct {
	packs map[string]models.PackDefinition
}

// LoadFromFS loads all .json files from the given filesystem into the store.
func LoadFromFS(fsys fs.FS) (*Store, error) {
	store := &Store{packs: make(map[string]models.PackDefinition)}

	entries, err := fs.ReadDir(fsys, ".")
	if err != nil {
		return nil, fmt.Errorf("reading pack directory: %w", err)
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
			return nil, fmt.Errorf("reading %s: %w", name, err)
		}

		var pack models.PackDefinition
		if err := json.Unmarshal(data, &pack); err != nil {
			return nil, fmt.Errorf("parsing %s: %w", name, err)
		}

		store.packs[pack.Id] = pack
	}

	return store, nil
}

// Get returns a pack definition by ID, or false if not found.
func (s *Store) Get(id string) (models.PackDefinition, bool) {
	p, ok := s.packs[id]
	return p, ok
}

// List returns all pack definitions.
func (s *Store) List() []models.PackDefinition {
	result := make([]models.PackDefinition, 0, len(s.packs))
	for _, p := range s.packs {
		result = append(result, p)
	}
	return result
}

// ListSummary returns pack summaries (id, version, name only).
func (s *Store) ListSummary() []models.Pack {
	result := make([]models.Pack, 0, len(s.packs))
	for _, p := range s.packs {
		result = append(result, models.Pack{
			Id:      p.Id,
			Version: p.Version,
			Name:    p.Name,
		})
	}
	return result
}
