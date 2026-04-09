package id.cachet.wallet.testfixtures

import id.cachet.wallet.network.PackSummary
import id.cachet.wallet.network.VerifiableCredentialDTO
import id.cachet.wallet.network.VerificationSession
import id.cachet.wallet.network.VerifierClient
import id.cachet.wallet.network.VerifyResponseDTO

class FakeVerifierClient(
    var packs: List<PackSummary> = emptyList(),
    var session: VerificationSession = VerificationSession(
        sessionId = "fake-session",
        nonce = "fake-nonce",
        verifierDid = "did:web:example.com"
    ),
    var verifyResponse: VerifyResponseDTO = VerifyResponseDTO(
        cachet = "",
        freshness = "fresh"
    )
) : VerifierClient {

    override suspend fun listPacks(): List<PackSummary> = packs

    override suspend fun createSession(
        packId: String?,
        question: String?,
        predicates: List<String>?
    ): VerificationSession = session

    override suspend fun verifyPresentation(
        policyId: String,
        credentials: List<VerifiableCredentialDTO>
    ): VerifyResponseDTO = verifyResponse

    override suspend fun verifySDJWTPresentation(
        policyId: String,
        sdJwtCredentials: List<String>,
        sessionId: String?
    ): VerifyResponseDTO = verifyResponse
}
