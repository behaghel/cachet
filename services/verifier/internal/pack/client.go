package pack

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"github.com/cachet-id/cachet/generated/go/models"
)

// Client fetches pack definitions from the Registry service.
type Client struct {
	baseURL    string
	httpClient *http.Client
}

// NewClient creates a pack client pointing at the given registry base URL.
func NewClient(baseURL string) *Client {
	return &Client{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// ListPacks fetches all pack definitions from the registry.
func (c *Client) ListPacks() ([]models.PackDefinition, error) {
	resp, err := c.httpClient.Get(c.baseURL + "/registry/packs")
	if err != nil {
		return nil, fmt.Errorf("fetching packs: %w", err)
	}
	defer resp.Body.Close() //nolint:errcheck

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("registry returned %d", resp.StatusCode)
	}

	var packs []models.PackDefinition
	if err := json.NewDecoder(resp.Body).Decode(&packs); err != nil {
		return nil, fmt.Errorf("decoding packs: %w", err)
	}
	return packs, nil
}

// GetPack fetches a single pack definition by ID.
func (c *Client) GetPack(packID string) (models.PackDefinition, error) {
	resp, err := c.httpClient.Get(c.baseURL + "/registry/packs/" + url.PathEscape(packID))
	if err != nil {
		return models.PackDefinition{}, fmt.Errorf("fetching pack %s: %w", packID, err)
	}
	defer resp.Body.Close() //nolint:errcheck

	if resp.StatusCode == http.StatusNotFound {
		return models.PackDefinition{}, fmt.Errorf("pack not found: %s", packID)
	}
	if resp.StatusCode != http.StatusOK {
		return models.PackDefinition{}, fmt.Errorf("registry returned %d for pack %s", resp.StatusCode, packID)
	}

	var pack models.PackDefinition
	if err := json.NewDecoder(resp.Body).Decode(&pack); err != nil {
		return models.PackDefinition{}, fmt.Errorf("decoding pack %s: %w", packID, err)
	}
	return pack, nil
}

// ListSummary fetches pack definitions and returns summaries.
func (c *Client) ListSummary() ([]models.Pack, error) {
	packs, err := c.ListPacks()
	if err != nil {
		return nil, err
	}
	summaries := make([]models.Pack, len(packs))
	for i, p := range packs {
		summaries[i] = models.Pack{Id: p.Id, Version: p.Version, Name: p.Name}
	}
	return summaries, nil
}
