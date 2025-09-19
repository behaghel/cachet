# Configuration

Cachet centralises environment configuration in [`config/app-config.json`](../config/app-config.json).
Each environment (local, staging, production, …) is declared exactly once inside this file so backend
services, mobile clients, and CI pipelines cannot drift apart.

## File Structure

```json
{
  "defaultEnvironment": "local",
  "environments": {
    "local": { "services": { … } },
    "staging": { "services": { … } },
    "production": { "services": { … } }
  }
}
```

- `defaultEnvironment` is used when no override is provided.
- Every environment block defines the canonical host/port/public URL values for each service.

## Backend / Services

All Go services load configuration through `services/common/config`:

```go
cfg := config.MustLoad()
port := config.ResolvePort("PORT", cfg.Services.Verifier.Port)
```

- Override the config file location with `CACHET_CONFIG_PATH`.
- Select a specific environment with `CACHET_ENV` (defaults to the value declared in the JSON).
- `ResolvePort` still honours per-process `PORT` overrides when necessary.

`devenv` exports `CACHET_CONFIG_PATH` and `CACHET_ENV=local` so commands such as
`devenv shell -- dev:up` automatically pick up the shared configuration.

## Mobile Build Integration

`androidApp/build.gradle.kts` parses the same JSON file during configuration. You can switch
environments by supplying `-PcachetEnv=<env>` when invoking Gradle; otherwise it uses the
JSON default.

The build populates `BuildConfig.CACHET_ENV` and `BuildConfig.ISSUANCE_BASE_URL`, and the Android
Koin bootstrap forwards those values into the shared Kotlin module via Koin properties so the UI and
shared business logic all reference the same URLs the backend advertises.

When running locally the Android build script now detects your host machine's IPv4 address (e.g. `192.168.x.x`) and, by default, points `BuildConfig.ISSUANCE_BASE_URL` at that address so physical devices can connect without extra setup. If detection fails or you need a custom host, override the base URL by exporting `CACHET_ISSUANCE_BASE_URL=http://<your-host>:8090` or passing `-PcachetIssuanceBaseUrl=http://<your-host>:8090` to Gradle. The overrides take precedence over the detected address and the `emulatorUrl`/`publicUrl` values from `app-config.json`.

Instrumentation tests use the same bootstrap path, keeping test and runtime configurations aligned.

## Adding or Updating Configuration

1. Extend `config/app-config.json` inside the appropriate environment (or add a new one).
2. Update `services/common/config` structs if new fields are required.
3. Consume the new value through the config package (Go) or Gradle/Koin wiring (Kotlin).

Following this workflow keeps configuration DRY and prevents drift between local development,
staging, and production environments.
