#!/usr/bin/env bash
# Verify devenv.nix Android SDK versions match mobile/gradle/libs.versions.toml
# Run from repo root. Fails CI if versions drift.
set -euo pipefail

TOML="mobile/gradle/libs.versions.toml"
NIX="devenv.nix"

if [ ! -f "$TOML" ]; then echo "ERROR: $TOML not found"; exit 1; fi
if [ ! -f "$NIX" ]; then echo "ERROR: $NIX not found"; exit 1; fi

# Extract from TOML
TOML_COMPILE_SDK=$(grep '^compileSdk' "$TOML" | sed 's/.*= *"\(.*\)"/\1/')
TOML_BUILD_TOOLS=$(grep '^buildTools' "$TOML" | sed 's/.*= *"\(.*\)"/\1/')

# Extract from Nix
NIX_COMPILE_SDK=$(grep 'platforms.version' "$NIX" | grep -oE '[0-9]+')
NIX_BUILD_TOOLS=$(grep 'buildTools.version' "$NIX" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')

FAIL=0
if [ "$TOML_COMPILE_SDK" != "$NIX_COMPILE_SDK" ]; then
  echo "MISMATCH: compileSdk — libs.versions.toml=$TOML_COMPILE_SDK, devenv.nix=$NIX_COMPILE_SDK"
  FAIL=1
fi
if [ "$TOML_BUILD_TOOLS" != "$NIX_BUILD_TOOLS" ]; then
  echo "MISMATCH: buildTools — libs.versions.toml=$TOML_BUILD_TOOLS, devenv.nix=$NIX_BUILD_TOOLS"
  FAIL=1
fi

if [ "$FAIL" -eq 1 ]; then
  echo "ERROR: Android SDK versions in devenv.nix and $TOML are out of sync."
  echo "The TOML file is the source of truth — update devenv.nix to match."
  exit 1
fi

echo "Android SDK versions in sync (compileSdk=$TOML_COMPILE_SDK, buildTools=$TOML_BUILD_TOOLS)"
