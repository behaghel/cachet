# CI/CD Optimization Guide

## Caching Strategy Implemented

### 🚀 Performance Improvements Applied

Our CI/CD pipeline now includes comprehensive caching that will dramatically speed up builds after the first run:

#### 1. **Nix Store Caching** 
- Caches the entire Nix store (`/nix/store`) including all devenv dependencies
- **Expected speedup**: 5-10x faster devenv builds after first run
- Cache key: Based on `devenv.nix` and `devenv.lock` files

#### 2. **Devenv Profile Caching**
- Caches devenv installation and profiles
- Prevents reinstalling devenv on every run
- **Expected speedup**: 2-3 minutes saved per job

#### 3. **Go Modules Comprehensive Caching** 
- Caches Go build cache and module downloads across all services
- **Expected speedup**: 30s-2min saved on dependency downloads

#### 4. **Android/Gradle Multi-layer Caching**
- Gradle caches, wrapper, build outputs
- Android SDK components from devenv
- **Expected speedup**: 3-5 minutes saved on Android builds

#### 5. **Smart Cache Keys**
- Uses content hashes of key files for precise cache invalidation
- Includes fallback cache restoration for partial matches

### 🔧 Optional Performance Enhancement

For even better performance, you can add a **Cachix authentication token**:

1. Go to [cachix.org](https://cachix.org) and create an account
2. Create an authentication token
3. Add it as a GitHub secret: `CACHIX_AUTH_TOKEN`

This enables:
- Faster cache uploads/downloads
- Priority access to Cachix CDN
- Better reliability for large cache objects

**Without the token**: Public Cachix access (still very fast)  
**With the token**: Authenticated access (optimal performance)

### 📊 Expected Performance After Optimization

| Job | Before | After Caching | After Smart Cleanup | Total Speedup |
|-----|--------|---------------|-------------------|-------------- |
| Backend | ~8-12 min | ~3-5 min | ~2-4 min | **3-4x faster** |
| Android | ~12-18 min | ~4-7 min | ~3-5 min | **4-5x faster** |
| Schema | ~1-2 min | ~30-60s | ~30-45s | **2-3x faster** |

### 🚀 Smart Disk Space Management

**New Conditional Cleanup Strategy:**
- **Checks disk space first** (takes ~2 seconds)
- **Only runs cleanup when needed** (< 6GB backend, < 8GB Android)
- **Skips cleanup entirely** when sufficient space available
- **Uses minimal, targeted cleanup** when triggered

**Performance Impact:**
- **First run**: Still slow (builds caches, may need cleanup)
- **Subsequent runs**: Often skips cleanup entirely = **2-4 minutes saved**
- **Cache hit rate**: ~80-90% after initial runs

### 🎯 Cache Invalidation Strategy

Caches are automatically invalidated when:
- `devenv.nix` or `devenv.lock` changes (full rebuild)
- `go.mod` or `go.sum` files change (Go deps rebuild)
- Gradle files change (Android deps rebuild)

This ensures builds are always correct while maximizing cache reuse.