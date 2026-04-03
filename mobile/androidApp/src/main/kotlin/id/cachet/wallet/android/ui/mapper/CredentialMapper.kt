package id.cachet.wallet.android.ui.mapper

import id.cachet.wallet.android.ui.components.BadgeType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.model.CredentialCardUi
import id.cachet.wallet.android.ui.model.VaultSummaryUi
import id.cachet.wallet.domain.model.CredentialSubject
import id.cachet.wallet.domain.model.StoredCredential
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

object CredentialMapper {

    fun toCardUi(stored: StoredCredential): CredentialCardUi {
        val vc = stored.credential
        val subject = vc.credentialSubject
        val issuer = getIssuerDisplayName(vc.issuer)
        val tier = getTierLabel(subject.verificationLevel)
        val expiry = getExpiryLabel(vc.expirationDate)
        return CredentialCardUi(
            localId = stored.localId,
            displayName = getCredentialDisplayName(vc.type),
            issuerLine = buildIssuerLine(issuer, tier, expiry),
            freshnessLabel = freshnessLabel(stored.createdAt),
            isRevoked = stored.isRevoked,
            badgeType = badgeTypeForTypes(vc.type),
            trustStatus = if (stored.isRevoked) TrustStatus.REVOKED else TrustStatus.VERIFIED,
            predicates = getPredicates(subject),
            sharesSummary = "Shared 3 times  ·  Last used 2 days ago"
        )
    }

    fun toVaultSummary(credentials: List<StoredCredential>): VaultSummaryUi {
        val verified = credentials.count { !it.isRevoked }
        return VaultSummaryUi(
            totalCount = credentials.size,
            verifiedCount = verified,
            pendingCount = credentials.size - verified
        )
    }

    // ── Individual mappers (internal for testing) ──

    internal fun getCredentialDisplayName(types: List<String>): String = when {
        types.contains("IdentityCredential") -> "Identity Credential"
        types.contains("ProofOfAge") -> "Age Verification"
        else -> types.lastOrNull() ?: "Credential"
    }

    internal fun getIssuerDisplayName(issuer: String): String = when {
        issuer.contains("veriff") -> "Veriff"
        issuer.contains("cachet.id") -> "Cachet"
        issuer.contains("did:web:") -> issuer.substringAfter("did:web:").substringBefore("#")
        else -> issuer.take(24).let { if (issuer.length > 24) "$it…" else it }
    }

    internal fun badgeTypeForTypes(types: List<String>): BadgeType? = when {
        types.contains("IdentityCredential") -> BadgeType.IDENTITY
        types.contains("ProofOfAge") -> BadgeType.AGE
        types.contains("BackgroundCheck") -> BadgeType.CHILDCARE
        types.contains("SellerCredential") -> BadgeType.SELLER
        else -> null
    }

    internal fun getPredicates(subject: CredentialSubject): List<String> = buildList {
        subject.personalData?.age?.let { add("Age ${it}+") }
        subject.verified?.let { if (it) add("ID Verified") }
        subject.personalData?.nationality?.let { add("Nationality") }
        subject.verificationMethod?.let {
            if (it.contains("liveness", ignoreCase = true)) add("Liveness")
        }
        if (isEmpty()) add("Verified")
    }

    internal fun getTierLabel(verificationLevel: String?): String? = when {
        verificationLevel == null -> null
        verificationLevel.contains("premium", ignoreCase = true) -> "Premium tier"
        verificationLevel.contains("standard", ignoreCase = true) -> "Standard tier"
        verificationLevel.contains("basic", ignoreCase = true) -> "Basic tier"
        else -> verificationLevel.replaceFirstChar { it.uppercase() }
    }

    internal fun getExpiryLabel(expirationDate: String?): String? {
        if (expirationDate == null) return null
        val expiry = try { Instant.parse(expirationDate) } catch (_: Exception) { return null }
        if (expiry < Clock.System.now()) return "Expired"
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val date = kotlinx.datetime.TimeZone.currentSystemDefault().let { expiry.toLocalDateTime(it) }
        return "Expires ${monthNames[date.monthNumber - 1]} ${date.year}"
    }

    internal fun freshnessLabel(createdAt: Instant): String {
        val days = ((Clock.System.now() - createdAt).inWholeDays).toInt()
        return when {
            days < 1 -> "today"
            days < 30 -> "${days}d"
            days < 365 -> "${days / 30}mo"
            else -> "${days / 365}y"
        }
    }

    internal fun buildIssuerLine(issuer: String, tier: String?, expiry: String?): String = buildString {
        append("Issued by $issuer")
        if (tier != null) append("  ·  $tier")
        if (expiry != null) append("  ·  $expiry")
    }
}
