package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
)

type ServiceConfig struct {
	Host        string `json:"host"`
	Port        int    `json:"port"`
	PublicURL   string `json:"publicUrl"`
	EmulatorURL string `json:"emulatorUrl,omitempty"`
}

type ServicesConfig struct {
	Verifier        ServiceConfig `json:"verifier"`
	Registry        ServiceConfig `json:"registry"`
	ReceiptsLog     ServiceConfig `json:"receiptsLog"`
	IssuanceGateway ServiceConfig `json:"issuanceGateway"`
	ConnectorHub    ServiceConfig `json:"connectorHub"`
	TransparencyLog ServiceConfig `json:"transparencyLog"`
	VouchingService ServiceConfig `json:"vouchingService"`
}

type VeriffIntegrationConfig struct {
	BaseURL            string `json:"baseUrl"`
	WebhookExternalURL string `json:"webhookExternalUrl,omitempty"`
	APIKeyEnv          string `json:"apiKeyEnv,omitempty"`
	WebhookSecretEnv   string `json:"webhookSecretEnv,omitempty"`
}

type veriffRootConfig struct {
	DefaultIntegration string                             `json:"defaultIntegration"`
	Integrations       map[string]VeriffIntegrationConfig `json:"integrations"`
}

type VeriffConfig struct {
	DefaultIntegration string                             `json:"defaultIntegration"`
	ActiveIntegration  string                             `json:"activeIntegration"`
	Integrations       map[string]VeriffIntegrationConfig `json:"integrations"`
}

type environmentBlock struct {
	Services                ServicesConfig `json:"services"`
	ActiveVeriffIntegration string         `json:"activeVeriffIntegration,omitempty"`
	VeriffWebhookExternal   string         `json:"veriffWebhookExternalUrl,omitempty"`
}

type rootConfig struct {
	DefaultEnvironment string                      `json:"defaultEnvironment"`
	Veriff             veriffRootConfig            `json:"veriff"`
	Environments       map[string]environmentBlock `json:"environments"`
}

type Config struct {
	Environment string
	Services    ServicesConfig
	Veriff      VeriffConfig
}

var (
	loadedConfig *Config
	loadOnce     sync.Once
	loadErr      error
)

// Load returns the environment-specific application configuration, reading the
// JSON file once per process. Set CACHET_CONFIG_PATH to override the config
// location and CACHET_ENV to select a non-default environment.
func Load() (*Config, error) {
	loadOnce.Do(func() {
		path, err := resolveConfigPath()
		if err != nil {
			loadErr = err
			return
		}

		bytes, err := os.ReadFile(path)
		if err != nil {
			loadErr = fmt.Errorf("config: failed to read %s: %w", path, err)
			return
		}

		var root rootConfig
		if err := json.Unmarshal(bytes, &root); err != nil {
			loadErr = fmt.Errorf("config: failed to parse %s: %w", path, err)
			return
		}

		env := determineEnvironment(root.DefaultEnvironment)
		envBlock, ok := root.Environments[env]
		if !ok {
			loadErr = fmt.Errorf("config: environment %q not defined in %s", env, path)
			return
		}

		services := envBlock.Services
		applyServiceDefaults(&services)

		veriffConfig, err := buildVeriffConfig(root.Veriff, envBlock.ActiveVeriffIntegration, envBlock.VeriffWebhookExternal, path, env)
		if err != nil {
			loadErr = err
			return
		}

		loadedConfig = &Config{
			Environment: env,
			Services:    services,
			Veriff:      veriffConfig,
		}
	})

	return loadedConfig, loadErr
}

// MustLoad returns configuration or panics if it cannot be loaded.
func MustLoad() *Config {
	cfg, err := Load()
	if err != nil {
		panic(err)
	}
	return cfg
}

// ResolvePort returns the port as a string, allowing environment overrides.
func ResolvePort(envVar string, defaultPort int) string {
	if value := os.Getenv(envVar); value != "" {
		return value
	}
	return fmt.Sprintf("%d", defaultPort)
}

func determineEnvironment(defaultEnv string) string {
	if env := os.Getenv("CACHET_ENV"); env != "" {
		return env
	}
	if defaultEnv != "" {
		return defaultEnv
	}
	return "local"
}

func resolveConfigPath() (string, error) {
	if explicit := os.Getenv("CACHET_CONFIG_PATH"); explicit != "" {
		if fileExists(explicit) {
			return explicit, nil
		}
		return "", fmt.Errorf("config: CACHET_CONFIG_PATH=%s does not exist", explicit)
	}

	candidates := []string{
		"config/app-config.json",
		"../config/app-config.json",
		"../../config/app-config.json",
		"../../../config/app-config.json",
	}

	for _, candidate := range candidates {
		if fileExists(candidate) {
			return candidate, nil
		}
	}

	cwd, _ := os.Getwd()
	return "", fmt.Errorf("config: could not locate app-config.json from %s; set CACHET_CONFIG_PATH", cwd)
}

func fileExists(path string) bool {
	if path == "" {
		return false
	}

	if !filepath.IsAbs(path) {
		if abs, err := filepath.Abs(path); err == nil {
			path = abs
		}
	}

	info, err := os.Stat(path)
	if err != nil {
		return false
	}

	return !info.IsDir()
}

func applyServiceDefaults(s *ServicesConfig) {
	fallbackEmulator := func(svc *ServiceConfig) {
		if svc.PublicURL != "" && svc.EmulatorURL == "" {
			svc.EmulatorURL = svc.PublicURL
		}
	}

	fallbackEmulator(&s.Verifier)
	fallbackEmulator(&s.Registry)
	fallbackEmulator(&s.ReceiptsLog)
	fallbackEmulator(&s.IssuanceGateway)
	fallbackEmulator(&s.ConnectorHub)
	fallbackEmulator(&s.TransparencyLog)
	fallbackEmulator(&s.VouchingService)
}

func buildVeriffConfig(root veriffRootConfig, activeIntegration, overrideWebhook, path, env string) (VeriffConfig, error) {
	if len(root.Integrations) == 0 {
		return VeriffConfig{}, fmt.Errorf("config: veriff.integrations is empty in %s", path)
	}

	defaultIntegration := root.DefaultIntegration
	if defaultIntegration == "" {
		// Pick deterministic default (first key) if not specified
		for name := range root.Integrations {
			defaultIntegration = name
			break
		}
	}

	if _, ok := root.Integrations[defaultIntegration]; !ok {
		return VeriffConfig{}, fmt.Errorf("config: veriff.defaultIntegration %q not defined in integrations (file: %s)", defaultIntegration, path)
	}

	resolvedActive := activeIntegration
	if resolvedActive == "" {
		resolvedActive = defaultIntegration
	}

	if _, ok := root.Integrations[resolvedActive]; !ok {
		return VeriffConfig{}, fmt.Errorf("config: environment %q references unknown Veriff integration %q", env, resolvedActive)
	}

	copiedIntegrations := make(map[string]VeriffIntegrationConfig, len(root.Integrations))
	for name, cfg := range root.Integrations {
		if overrideWebhook != "" {
			cfg.WebhookExternalURL = overrideWebhook
		}
		copiedIntegrations[name] = cfg
	}

	return VeriffConfig{
		DefaultIntegration: defaultIntegration,
		ActiveIntegration:  resolvedActive,
		Integrations:       copiedIntegrations,
	}, nil
}
