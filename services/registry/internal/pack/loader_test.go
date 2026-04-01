package pack

import (
	"testing"
	"testing/fstest"

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
