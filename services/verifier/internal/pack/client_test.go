package pack

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func mockRegistry() *httptest.Server {
	mux := http.NewServeMux()

	testPack := models.PackDefinition{
		Id:      "pack.test",
		Version: "0.1.0",
		Name:    "Test Pack",
		Purpose: "Testing",
		Badge:   models.BadgeDefinition{Label: "Test", Ttl: "P90D"},
		Predicates: []models.PredicateDefinition{
			{Id: "age.ge.18", Claim: "age", Operator: models.GreaterThanEqual, ProofType: models.SdJwt, IssuersAccepted: []string{"did:veriff:*"}, Value: float64(18)},
		},
	}

	mux.HandleFunc("/registry/packs", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]models.PackDefinition{testPack})
	})
	mux.HandleFunc("/registry/packs/pack.test", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(testPack)
	})
	mux.HandleFunc("/registry/packs/nonexistent", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]string{"error": "not_found"})
	})

	return httptest.NewServer(mux)
}

func TestListPacks(t *testing.T) {
	srv := mockRegistry()
	defer srv.Close()

	client := NewClient(srv.URL)
	packs, err := client.ListPacks()
	require.NoError(t, err)
	assert.Len(t, packs, 1)
	assert.Equal(t, "pack.test", packs[0].Id)
}

func TestGetPack_Found(t *testing.T) {
	srv := mockRegistry()
	defer srv.Close()

	client := NewClient(srv.URL)
	pack, err := client.GetPack("pack.test")
	require.NoError(t, err)
	assert.Equal(t, "pack.test", pack.Id)
	assert.Len(t, pack.Predicates, 1)
}

func TestGetPack_NotFound(t *testing.T) {
	srv := mockRegistry()
	defer srv.Close()

	client := NewClient(srv.URL)
	_, err := client.GetPack("nonexistent")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "pack not found")
}

func TestListSummary(t *testing.T) {
	srv := mockRegistry()
	defer srv.Close()

	client := NewClient(srv.URL)
	summaries, err := client.ListSummary()
	require.NoError(t, err)
	assert.Len(t, summaries, 1)
	assert.Equal(t, "pack.test", summaries[0].Id)
	assert.Equal(t, "Test Pack", summaries[0].Name)
}

func TestListPacks_ServerDown(t *testing.T) {
	client := NewClient("http://localhost:1") // nothing listening
	_, err := client.ListPacks()
	assert.Error(t, err)
}
