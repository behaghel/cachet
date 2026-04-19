# Disaster Recovery Playbook

**Audience:** On-call engineers, incident commanders
**Principle:** Staging IS production for the alpha. Every procedure here applies to staging.
**Last reviewed:** 2026-04-19

---

## Scenario 1: Issuer Signing Key Loss (CATASTROPHIC)

**What happens:** Every SD-JWT credential ever issued becomes permanently unverifiable. The JWKS endpoint serves a new public key; all existing credentials were signed with the lost key. Holders cannot prove anything. There is no remediation short of re-issuing every credential.

**Detection:**
- JWKS public key fingerprint changes unexpectedly after a deploy
- Verifier logs show 100% signature verification failures
- Holder complaints: "my cachet stopped working"

**Recovery:**
1. **If backup exists:** Restore the issuer key PEM from GCP Secret Manager / offline backup into `ISSUER_KEY_FILE` or KMS. Redeploy issuance-gateway. Verify JWKS fingerprint matches the original.
2. **If no backup:** The key is irrecoverable. Begin mass re-issuance:
   - Notify all holders via push notification
   - Revoke all credentials signed with the lost `kid` via StatusList
   - Generate new key pair, publish new JWKS
   - Trigger re-verification flow for all active holders
   - Update verifier trust anchors with new issuer DID/key

**Prevention:**
- Store issuer key in GCP Cloud KMS (HSM-backed, never exported) -- see #83
- KMS keys are automatically replicated within the region
- For file-based keys (dev/staging): encrypted backup in Secret Manager + offline copy
- Test key restore procedure quarterly

**Related:** #83

---

## Scenario 2: Issuer Signing Key Compromise (CATASTROPHIC)

**What happens:** An attacker with the private key can forge arbitrary credentials indistinguishable from legitimate ones. They can create fake identities, backdate credentials, and undermine the entire trust model.

**Detection:**
- KMS access logs show unauthorized `AsymmetricSign` calls
- Anomalous signing rate (spike in `cachet_credentials_issued` metric)
- Reports of credentials for non-existent verification sessions
- External report / bug bounty disclosure

**Recovery:**
1. **Immediate (< 15 min):**
   - Rotate the issuer key: generate new key pair in KMS, update `CACHET_KMS_KEY_NAME`
   - Redeploy issuance-gateway with new key
   - Revoke the old key version in KMS (disable, do not destroy -- needed for forensics)
2. **Short-term (< 1 hour):**
   - Mass revoke all credentials signed with the compromised `kid`
   - Publish key revocation notice via DID document update
   - Notify verifiers to refresh JWKS
3. **Medium-term (< 24 hours):**
   - Audit KMS access logs to determine scope of compromise
   - Rotate all secrets (webhook secret, JWT secret, admin API key, DB password)
   - Notify affected holders to re-verify
4. **Post-incident:**
   - Root cause analysis: how was the key accessed?
   - Implement additional controls (IP allowlist on KMS, VPC Service Controls)

**Prevention:**
- HSM-backed keys via Cloud KMS (private key never exported)
- Principle of least privilege: only issuance-gateway service account can sign
- KMS audit logging enabled and monitored
- Alert on signing rate anomalies

**Related:** #83

---

## Scenario 3: Database Loss (HIGH)

**What happens:** Cloud SQL instance data is lost (corruption, accidental deletion, failed migration). Session data, credential metadata, and application state are gone.

**Detection:**
- Service health checks fail (`/ready` returns 503)
- Cloud SQL instance status shows `SUSPENDED` or `FAILED`
- Application logs: connection refused / timeout errors

**Recovery:**
1. Check Cloud SQL automatic backups: `gcloud sql backups list --instance=cachet-db`
2. Restore from most recent backup: `gcloud sql backups restore <BACKUP_ID> --restore-instance=cachet-db`
3. For point-in-time recovery (if enabled): `gcloud sql instances clone cachet-db cachet-db-restored --point-in-time=<TIMESTAMP>`
4. Verify data integrity after restore
5. Redeploy services if connection strings changed

**Prevention:**
- Enable automated backups with 7-day retention (Cloud SQL default)
- Enable point-in-time recovery (binary logging)
- Test restore procedure monthly
- Document database schema migrations for manual recovery

**Related:** #130

---

## Scenario 4: Database Compromise / Data Exfiltration (HIGH)

**What happens:** Unauthorized access to Cloud SQL exposes session data, verification records, and potentially PII from Veriff sessions.

**Detection:**
- Cloud SQL audit logs show unauthorized connections
- Unexpected queries from unknown IP addresses
- Data exfiltration alerts from Cloud Armor / VPC flow logs

**Recovery:**
1. **Immediate:** Revoke compromised credentials, rotate database password
2. Update `database-url` secret in Secret Manager, redeploy all services
3. Audit Cloud SQL access logs for scope of breach
4. If PII was exposed: notify affected users per GDPR Article 33 (72-hour window)
5. Enable SSL-only connections if not already enforced

**Prevention:**
- Cloud SQL Auth Proxy or private VPC connection (no public IP)
- SSL-required connections
- Minimal PII in database (verification status only, not raw biometric data)
- Database user with minimal privileges (not `postgres` superuser)
- Cloud SQL audit logging enabled

**Related:** #131

---

## Scenario 5: Secret Manager Breach (CRITICAL)

**What happens:** All secrets in GCP Secret Manager are exposed: database URL, JWT secret, webhook secret, KMS key references, admin API key.

**Detection:**
- Secret Manager audit logs show unauthorized `AccessSecretVersion` calls
- IAM policy changes on secret resources
- Alerts from GCP Security Command Center

**Recovery:**
1. **Immediate (< 15 min):** Rotate ALL secrets:
   - Database password: `gcloud sql users set-password` + update Secret Manager
   - JWT secret: generate new, update Secret Manager
   - Webhook secret: coordinate with Veriff to rotate
   - Admin API key: generate new, update Secret Manager
   - KMS key: if KMS key name was exposed, the key itself is safe (HSM-backed), but rotate service account credentials
2. Redeploy all services to pick up new secrets
3. Invalidate all active sessions and OAuth tokens
4. Audit IAM policies: who had access, when was it granted?

**Prevention:**
- Principle of least privilege: per-service service accounts with minimal secret access
- Secret Manager audit logging
- No secret values in environment variables at build time (inject at runtime only)
- Secrets never logged (zerolog config excludes secret fields)

**Related:** #132

---

## Scenario 6: StatusList Corruption (HIGH)

**What happens:** The StatusList2021 bitstring file is corrupted or lost. Revocation state is unknown -- revoked credentials may appear valid, or valid credentials may appear revoked.

**Detection:**
- `GET /status/1/info` returns unexpected `allocated`/`revoked` counts
- Verifier reports inconsistent revocation results
- StatusList file missing or JSON parse errors in logs

**Recovery:**
1. If file persistence is enabled, restore `statuslists.json` from backup
2. If no backup: the in-memory state since last restart is the best available
3. Cross-reference with admin audit logs to reconstruct revocation history
4. Worst case: re-issue credentials to all holders with fresh status indices

**Prevention:**
- Persist StatusList to a durable store (Cloud Storage or Cloud SQL) instead of local file
- Backup StatusList file alongside database backups
- Audit log every revocation operation (already implemented via `common.AuditLog`)

**Related:** #133

---

## Scenario 7: Service Outage (MEDIUM)

**What happens:** One or more Cloud Run services are down. Verification, issuance, or relay stops working.

**Detection:**
- "Service Down" alert fires (no requests for 10 min)
- Health check failures on `/health` endpoint
- Cloud Run console shows 0 instances or repeated crash loops

**Recovery:**
1. Check Cloud Run logs: `gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=cachet-<service>"`
2. If crash loop: check recent deploy, rollback if needed: `gcloud run services update-traffic <service> --to-revisions=<previous>=100`
3. If resource exhaustion: increase min instances or memory
4. If dependency failure (e.g., issuance-gateway can't reach registry): check inter-service connectivity

**Prevention:**
- Cloud Run auto-scaling with min instances > 0 for critical services
- Graceful shutdown (already implemented: 10s drain)
- Readiness checks gate traffic to healthy instances
- Deploy with traffic migration (canary) not instant cutover

**Related:** #134

---

## Scenario 8: DNS / TLS Failure (MEDIUM)

**What happens:** `cachet.vc` subdomains are unreachable. Services are running but users can't connect. Mobile app shows connection errors.

**Detection:**
- External uptime monitor (e.g., Cloud Monitoring uptime check) alerts
- Users report "can't connect" errors
- `dig verify.cachet.vc` returns NXDOMAIN or wrong IP

**Recovery:**
1. Check Cloud DNS zone: `gcloud dns record-sets list --zone=cachet-vc`
2. Check domain registrar: is the domain expired or transferred?
3. Check TLS certificates: Cloud Run managed certs auto-renew, but custom domains may need manual intervention
4. If DNS propagation issue: TTL-dependent, may take up to 1 hour
5. If registrar compromise: contact registrar emergency support, enable registrar lock

**Prevention:**
- Domain registrar lock enabled
- DNSSEC enabled on `cachet.vc`
- Low TTL (300s) on critical records for fast failover
- Cloud Run managed TLS (auto-renewal)
- External uptime monitoring on all subdomains

**Related:** #135

---

## Scenario 9: Dependency / Supply Chain Compromise (HIGH)

**What happens:** A Go module, Docker base image, or CI action is compromised. Malicious code runs in our services.

**Detection:**
- Dependabot / GitHub security advisory alerts
- Unexpected behavior in services after a dependency update
- gosec or Semgrep flags new vulnerabilities
- Anomalous network traffic from containers

**Recovery:**
1. Identify the compromised dependency from advisory
2. Pin to last known-good version in `go.mod`
3. Rebuild and redeploy all affected services
4. Audit what the compromised code had access to (secrets, network, data)
5. Rotate any secrets the compromised service could access
6. If container base image: rebuild from verified base, check image signatures

**Prevention:**
- Dependabot configured for weekly updates (already in place)
- Docker base images: use distroless (minimal attack surface, already in place)
- Pin CI actions to SHA, not tags
- Run `gosec` and `ci:security` on every PR (already in place)
- Enable Go module checksum database (`GONOSUMCHECK` not set)

**Related:** #136

---

## Scenario 10: Admin API Key Leak (HIGH)

**What happens:** The admin API key is exposed (committed to git, leaked in logs, shared insecurely). Attacker can revoke credentials, view sessions, and modify packs.

**Detection:**
- GitHub secret scanning alert
- Unexpected admin operations in audit logs
- Sessions or credentials revoked without admin action

**Recovery:**
1. **Immediate:** Rotate `ADMIN_API_KEY` in Secret Manager, redeploy admin service
2. Audit admin API logs for unauthorized operations
3. Reverse any unauthorized changes (un-revoke credentials if possible)
4. If credentials were revoked: notify affected holders

**Prevention:**
- Never commit API keys to git (pre-commit hook, `.env` is gitignored)
- Implement RBAC with per-user sessions (replace single global key)
- Rate limit admin API endpoints
- Require MFA for admin access (future)
- Audit log all admin operations (already implemented)

**Related:** #137

---

## Appendix A: Emergency Contacts

| Role | Contact | Escalation |
|------|---------|------------|
| On-call engineer | TBD | Slack #cachet-ops |
| Incident commander | TBD | Phone |
| GCP support | TBD | Console ticket |
| Domain registrar | TBD | Emergency line |
| Veriff integration | TBD | Partner contact |

## Appendix B: Runbook Testing Schedule

| Procedure | Frequency | Last tested | Next due |
|-----------|-----------|-------------|----------|
| Database restore from backup | Monthly | - | Before alpha launch |
| Issuer key restore from KMS backup | Quarterly | - | Before alpha launch |
| Secret rotation (all secrets) | Quarterly | - | Before alpha launch |
| Service rollback | Monthly | - | Before alpha launch |
| Mass credential revocation | Before launch | - | Before alpha launch |
| DNS failover | Before launch | - | Before alpha launch |

## Appendix C: Key Fingerprint Verification

To verify the issuer key has not changed unexpectedly:

```bash
# Fetch JWKS and extract key fingerprint
curl -s https://api.cachet.vc/.well-known/jwks.json | \
  jq -r '.keys[0] | .x + .y' | \
  openssl dgst -sha256
```

Compare against the known-good fingerprint stored in the incident response vault.
