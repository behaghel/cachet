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

type environmentBlock struct {
	Services ServicesConfig `json:"services"`
}

type rootConfig struct {
	DefaultEnvironment string                      `json:"defaultEnvironment"`
	Environments       map[string]environmentBlock `json:"environments"`
}

type Config struct {
	Environment string
	Services    ServicesConfig
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

		loadedConfig = &Config{
			Environment: env,
			Services:    envBlock.Services,
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
