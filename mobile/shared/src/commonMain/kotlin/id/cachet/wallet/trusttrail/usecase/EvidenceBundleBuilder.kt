package id.cachet.wallet.trusttrail.usecase

import id.cachet.wallet.trusttrail.model.BundleClaim
import id.cachet.wallet.trusttrail.model.EmailEvidence
import id.cachet.wallet.trusttrail.model.EvidenceBundle

/**
 * Builds an evidence bundle from extracted evidence, respecting user deselections.
 *
 * Claim IDs follow the format "platform:type:index" for deselection tracking.
 */
object EvidenceBundleBuilder {

    /**
     * Build an evidence bundle from a list of extracted evidence.
     *
     * - Rejected evidence is excluded.
     * - Deselected claims (by ID) are excluded.
     * - Each claim is enriched with platform, date, trust level, and DKIM proof.
     */
    fun build(
        evidence: List<EmailEvidence>,
        deselectedClaimIds: Set<String>,
    ): EvidenceBundle {
        val bundleClaims = mutableListOf<BundleClaim>()

        for (ev in evidence) {
            if (ev.rejected) continue

            for ((index, claim) in ev.claims.withIndex()) {
                val claimId = "${ev.platform}:${claim.type}:$index"
                if (claimId in deselectedClaimIds) continue

                bundleClaims.add(
                    BundleClaim(
                        type = claim.type,
                        fields = claim.fields,
                        confidence = claim.confidence,
                        trustLevel = ev.trustLevel,
                        platform = ev.platform,
                        date = ev.receivedDate,
                        dkimDomain = ev.dkimDomain,
                    )
                )
            }
        }

        return EvidenceBundle(bundleClaims)
    }
}
