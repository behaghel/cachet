package pack

import (
	"os"
	"path/filepath"
	"testing"
	"testing/fstest"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

var testPackJSON = `{
  "id": "pack.test",
  "version": "0.1.0",
  "name": "Test Pack",
  "purpose": "Testing",
  "jurisdictions": ["ES"],
  "badge": {"label": "Test Badge", "ttl": "P90D", "jurisdiction": "ES"},
  "predicates": [
    {
      "id": "age.ge.18",
      "claim": "age",
      "operator": ">=",
      "value": 18,
      "issuersAccepted": ["did:veriff:*"],
      "proofType": "sd-jwt"
    },
    {
      "id": "identity.verified",
      "claim": "verified",
      "operator": "boolean",
      "value": true,
      "issuersAccepted": ["did:veriff:*"],
      "proofType": "sd-jwt"
    }
  ]
}`

func TestLoadFromFS(t *testing.T) {
	fsys := fstest.MapFS{
		"test-pack.json": &fstest.MapFile{Data: []byte(testPackJSON)},
		"readme.txt":     &fstest.MapFile{Data: []byte("not a pack")},
	}

	store, err := LoadFromFS(fsys)
	require.NoError(t, err)

	// Should load only the JSON file
	packs := store.List()
	assert.Len(t, packs, 1)
	assert.Equal(t, "pack.test", packs[0].Id)
	assert.Equal(t, "Test Pack", packs[0].Name)
	assert.Len(t, packs[0].Predicates, 2)
	assert.Equal(t, "age.ge.18", packs[0].Predicates[0].Id)
}

func TestLoadFromFS_Get(t *testing.T) {
	fsys := fstest.MapFS{
		"test-pack.json": &fstest.MapFile{Data: []byte(testPackJSON)},
	}

	store, err := LoadFromFS(fsys)
	require.NoError(t, err)

	pack, ok := store.Get("pack.test")
	assert.True(t, ok)
	assert.Equal(t, "Test Pack", pack.Name)

	_, ok = store.Get("nonexistent")
	assert.False(t, ok)
}

func TestLoadFromFS_ListSummary(t *testing.T) {
	fsys := fstest.MapFS{
		"test-pack.json": &fstest.MapFile{Data: []byte(testPackJSON)},
	}

	store, err := LoadFromFS(fsys)
	require.NoError(t, err)

	summaries := store.ListSummary()
	assert.Len(t, summaries, 1)
	assert.Equal(t, "pack.test", summaries[0].Id)
	assert.Equal(t, "0.1.0", summaries[0].Version)
	assert.Equal(t, "Test Pack", summaries[0].Name)
}

func TestLoadFromFS_InvalidJSON(t *testing.T) {
	fsys := fstest.MapFS{
		"bad.json": &fstest.MapFile{Data: []byte("not json")},
	}

	_, err := LoadFromFS(fsys)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "parsing bad.json")
}

func TestLoadFromFS_Empty(t *testing.T) {
	fsys := fstest.MapFS{}
	store, err := LoadFromFS(fsys)
	require.NoError(t, err)
	assert.Empty(t, store.List())
}

func TestOverlay_ShadowsEmbedded(t *testing.T) {
	embedded := fstest.MapFS{
		"test-pack.json": &fstest.MapFile{Data: []byte(testPackJSON)},
	}

	overlayDir := t.TempDir()
	overlayPack := `{"id":"pack.test","version":"2.0.0","name":"Updated Pack","purpose":"Testing","predicates":[{"id":"p1","claim":"c","operator":"boolean","value":true,"issuersAccepted":["*"],"proofType":"sd-jwt"}]}`
	require.NoError(t, os.WriteFile(filepath.Join(overlayDir, "pack.test.json"), []byte(overlayPack), 0o644))

	store, err := LoadWithOverlay(embedded, overlayDir)
	require.NoError(t, err)

	p, ok := store.Get("pack.test")
	require.True(t, ok)
	assert.Equal(t, "2.0.0", p.Version, "overlay should shadow embedded")
	assert.Equal(t, "Updated Pack", p.Name)
}

func TestOverlay_MissingDirIsOK(t *testing.T) {
	embedded := fstest.MapFS{
		"test-pack.json": &fstest.MapFile{Data: []byte(testPackJSON)},
	}

	store, err := LoadWithOverlay(embedded, "/nonexistent/dir/that/does/not/exist")
	require.NoError(t, err)
	assert.Len(t, store.List(), 1)
}

func TestPut_WritesToOverlay(t *testing.T) {
	overlayDir := t.TempDir()
	store, err := LoadWithOverlay(fstest.MapFS{}, overlayDir)
	require.NoError(t, err)

	p := models.PackDefinition{
		Id:      "pack.new",
		Version: "1.0.0",
		Name:    "New Pack",
		Purpose: "Test",
		Predicates: []models.PredicateDefinition{
			{Id: "p1", Claim: "c", Operator: "boolean"},
		},
	}
	require.NoError(t, store.Put(p))

	// Verify in-memory
	got, ok := store.Get("pack.new")
	require.True(t, ok)
	assert.Equal(t, "New Pack", got.Name)

	// Verify file on disk
	_, err = os.Stat(filepath.Join(overlayDir, "pack.new.json"))
	assert.NoError(t, err)
}

func TestPut_NoOverlayDir(t *testing.T) {
	store, err := LoadFromFS(fstest.MapFS{})
	require.NoError(t, err)

	err = store.Put(models.PackDefinition{Id: "test"})
	assert.ErrorContains(t, err, "no overlay directory")
}

func TestReload(t *testing.T) {
	overlayDir := t.TempDir()
	embedded := fstest.MapFS{
		"test-pack.json": &fstest.MapFile{Data: []byte(testPackJSON)},
	}

	store, err := LoadWithOverlay(embedded, overlayDir)
	require.NoError(t, err)
	assert.Len(t, store.List(), 1)

	// Write a new pack to overlay
	newPack := `{"id":"pack.added","version":"1.0.0","name":"Added","purpose":"Test","predicates":[{"id":"p1","claim":"c","operator":"boolean","value":true,"issuersAccepted":["*"],"proofType":"sd-jwt"}]}`
	require.NoError(t, os.WriteFile(filepath.Join(overlayDir, "added.json"), []byte(newPack), 0o644))

	require.NoError(t, store.Reload())
	assert.Len(t, store.List(), 2)
}

func TestSetEnabled(t *testing.T) {
	fsys := fstest.MapFS{
		"test-pack.json": &fstest.MapFile{Data: []byte(testPackJSON)},
	}

	store, err := LoadFromFS(fsys)
	require.NoError(t, err)

	// Initially enabled
	assert.True(t, store.IsEnabled("pack.test"))
	assert.Len(t, store.List(), 1)
	assert.Len(t, store.ListSummary(), 1)

	// Disable
	ok := store.SetEnabled("pack.test", false)
	assert.True(t, ok)
	assert.False(t, store.IsEnabled("pack.test"))
	assert.Len(t, store.List(), 0, "disabled packs excluded from List")
	assert.Len(t, store.ListSummary(), 0, "disabled packs excluded from ListSummary")

	// Still accessible via Get for admin
	_, found := store.Get("pack.test")
	assert.True(t, found, "disabled packs still in Get")

	// ListAll includes disabled
	assert.Len(t, store.ListAll(), 1)

	// Re-enable
	store.SetEnabled("pack.test", true)
	assert.True(t, store.IsEnabled("pack.test"))
	assert.Len(t, store.List(), 1)

	// Non-existent pack
	ok = store.SetEnabled("nonexistent", false)
	assert.False(t, ok)
}
