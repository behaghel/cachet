# CI/CD Pipeline Optimization Plan

## Current State
- Full pipeline runtime: ~30+ minutes
- Issues: Sequential builds, cache misses, full rebuilds for all changes
- Symptoms: "Failed to restore tar" warnings, long container builds

## Optimization Strategy

### Phase 1: Immediate Wins (Low Risk, High Impact)
**Target: Reduce to 15-20 minutes**

1. **Parallel Job Execution**
   - Run Android/Backend/Schema jobs fully in parallel
   - Parallelize container builds across services
   - Use job dependencies only where truly needed

2. **Cache Optimization** 
   - Fix "Failed to restore tar" warnings with better cache keys
   - Implement cache cleanup strategies
   - Use Cachix more effectively for Nix dependencies

3. **Selective Builds**
   - Only build changed services using path filters
   - Skip Android builds for backend-only changes
   - Skip container builds for schema/docs changes

### Phase 2: Medium-term Improvements (Moderate Risk)
**Target: Reduce to 10-15 minutes**

1. **DevEnv Container Optimization**
   - Use official devenv container image: `ghcr.io/cachix/devenv/devenv:latest`
   - Implement container-native task execution patterns
   - Pre-build base containers with common dependencies
   - Explore multi-stage container builds

2. **Artifact Reuse**
   - Share built artifacts between jobs
   - Cache Go modules, Android dependencies separately
   - Use Docker layer caching for containers

3. **Matrix Builds**
   ```yaml
   strategy:
     matrix:
       service: [verifier, registry, receipts, issuance]
   ```

### Phase 3: Architectural Changes (Higher Risk)
**Target: 5-10 minutes for fast feedback**

1. **Split Pipeline Strategy**
   ```
   Fast Feedback (5-10 min):
   - Linting, formatting
   - Unit tests
   - Basic builds
   
   Full Validation (20-30 min):
   - Integration tests  
   - Container builds
   - Deployment
   ```

2. **Incremental Builds**
   - Dependency graph analysis
   - Only rebuild affected services
   - Smart test selection based on changes

3. **Pre-built Infrastructure**
   - Nightly base image builds
   - Dependency pre-warming
   - Container layer optimization

## DevEnv-Specific Optimizations

Based on [devenv container docs](https://devenv.sh/integrations/devenv-container/):

1. **Container Integration Patterns**
   ```yaml
   # Use official devenv image
   - image: ghcr.io/cachix/devenv/devenv:latest
     script: devenv shell --no-nix-flakes your-command
   ```

2. **Task-Based Execution**
   ```yaml
   # Replace ad-hoc commands with defined tasks
   - run: devenv tasks run build:backend
   - run: devenv tasks run test:integration
   ```

3. **Reproducible Environments**
   - Leverage devenv.nix for consistent environments
   - Use container-native configurations
   - Implement proper caching strategies

## Implementation Priority

1. **Week 1**: Fix cache issues, enable more parallelization
2. **Week 2**: Implement selective builds and container optimizations  
3. **Week 3**: Evaluate pipeline splitting strategy
4. **Month 2**: Full architectural optimization

## Success Metrics

- **Developer Experience**: Fast feedback for typical changes (< 10 min)
- **Resource Efficiency**: Reduce unnecessary rebuilds by 60%
- **Reliability**: Maintain current test coverage and deployment success rate
- **Cost**: Optimize CI minutes usage

## Notes

- Maintain current deployment reliability during optimization
- Test changes on feature branches before applying to main pipeline
- Consider developer workflow impact (local dev should remain fast)
- Document optimizations for future maintenance

---
*Created during Phase A Enhanced Veriff Integration - defer implementation until primary objectives completed*