package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.network.CredentialResponse
import kotlin.time.Clock
import kotlinx.datetime.Instant

fun makeCredential(
    id: String = "urn:uuid:test-credential",
    types: List<String> = listOf("VerifiableCredential", "IdentityCredential"),
    issuer: String = "did:web:issuer.cachet.id",
    issuanceDate: String = "2026-01-15T10:00:00Z",
    expirationDate: String? = "2027-01-15T10:00:00Z",
    subjectId: String = "did:key:holder123",
    personalData: PersonalData? = PersonalData(age = 30, nationality = "FR", documentType = "passport"),
    verified: Boolean? = true,
    verificationLevel: String? = "premium",
    verificationMetrics: VerificationMetrics? = null,
    credentialStatus: CredentialStatus? = null
) = VerifiableCredential(
    id = id,
    context = listOf("https://www.w3.org/2018/credentials/v1"),
    type = types,
    issuer = issuer,
    issuanceDate = issuanceDate,
    expirationDate = expirationDate,
    credentialSubject = CredentialSubject(
        id = subjectId,
        personalData = personalData,
        verificationLevel = verificationLevel,
        verified = verified,
        verificationMetrics = verificationMetrics
    ),
    credentialStatus = credentialStatus
)

fun makeStoredCredential(
    localId: String = "local-1",
    credential: VerifiableCredential = makeCredential(),
    rawSdJwt: String? = null,
    keyAlias: String? = null,
    createdAt: Instant = Clock.System.now(),
    isRevoked: Boolean = false
) = StoredCredential(
    localId = localId,
    credential = credential,
    rawSdJwt = rawSdJwt,
    keyAlias = keyAlias,
    createdAt = createdAt,
    isRevoked = isRevoked
)

fun makeCredentialResponse(
    credential: VerifiableCredential = makeCredential(),
    format: String = "jwt_vc"
) = CredentialResponse(
    credential = credential,
    format = format
)

fun makeConsentReceipt(
    id: String = "receipt-1",
    purpose: String = "Age verification for online purchase",
    predicatesProven: List<String> = listOf("age_gte_18"),
    rpIdentifier: String = "did:web:shop.example.com",
    rpDisplayName: String = "Example Shop",
    credentialId: String = "urn:uuid:test-credential",
    retentionPeriodDays: Int = 90
) = ConsentReceipt(
    id = id,
    timestamp = Clock.System.now(),
    purpose = purpose,
    predicatesProven = predicatesProven,
    rpIdentifier = rpIdentifier,
    rpDisplayName = rpDisplayName,
    userConsent = ConsentDetails(
        explicitConsent = true,
        dataMinimizationAcknowledged = true,
        retentionPeriodUnderstood = true,
        retentionPeriodDays = retentionPeriodDays
    ),
    credentialId = credentialId
)

fun makePresentationRequest(
    rpIdentifier: String = "did:web:shop.example.com",
    rpDisplayName: String = "Example Shop",
    purpose: String = "Age verification for online purchase",
    requestedPredicates: List<String> = listOf("age_gte_18")
) = PresentationRequest(
    rpIdentifier = rpIdentifier,
    rpDisplayName = rpDisplayName,
    purpose = purpose,
    requestedPredicates = requestedPredicates
)
