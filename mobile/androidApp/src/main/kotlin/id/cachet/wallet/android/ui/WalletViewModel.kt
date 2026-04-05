package id.cachet.wallet.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.mapper.ActivityMapper
import id.cachet.wallet.android.ui.mapper.CachPackMapper
import id.cachet.wallet.android.ui.mapper.CredentialMapper
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.android.verification.VeriffResult
import id.cachet.wallet.android.verification.VeriffService
import id.cachet.wallet.domain.model.ConsentDetails
import id.cachet.wallet.domain.model.PresentationRequest
import id.cachet.wallet.domain.usecase.ConsentUseCase
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import id.cachet.wallet.domain.usecase.VerificationUseCase
import id.cachet.wallet.domain.usecase.VerifierSessionInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WalletViewModel(
    private val issuanceUseCase: IssuanceUseCase,
    private val veriffService: VeriffService,
    private val consentUseCase: ConsentUseCase,
    private val verificationUseCase: VerificationUseCase,
    private val demoMode: Boolean = false,
    private val demoEmpty: Boolean = false
) : ViewModel() {

    companion object {
        private const val TAG = "WalletViewModel"
    }

    private val _uiState = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _activityState = MutableStateFlow(ActivityUiState())
    val activityState: StateFlow<ActivityUiState> = _activityState.asStateFlow()

    /** Active verifier session — set when QR overlay is shown, consumed when verification completes. */
    private var activeVerifierSession: VerifierSessionInfo? = null

    init {
        if (demoEmpty) {
            _uiState.value = WalletUiState.Empty
        } else if (demoMode) {
            loadDemoCredentials()
        } else {
            loadCredentials()
            loadActivity()
        }
    }

    // ── Relay flow ──

    /**
     * Verifier side: create a relay session for QR-based verification.
     * Returns the QR payload string, or null if the relay is unavailable.
     */
    suspend fun createVerifierSession(packId: String, question: String, predicates: List<String>): String? {
        return try {
            val session = verificationUseCase.startVerifierSession(packId, question, predicates)
            activeVerifierSession = session
            Log.d(TAG, "Relay session created, QR: ${session.qrPayload}")
            session.qrPayload
        } catch (e: Exception) {
            Log.w(TAG, "Relay unavailable, falling back to static QR", e)
            null
        }
    }

    /**
     * Holder side: fetch the verification request from the relay.
     * Parses the QR payload to extract the request_uri and fetches the request.
     */
    suspend fun fetchRequestFromRelay(qrPayload: String): VerificationRequest? {
        return try {
            val requestUri = parseRequestUri(qrPayload) ?: return null
            val request = verificationUseCase.fetchVerificationRequest(requestUri)
            VerificationRequest(
                question = request.question,
                predicates = request.predicates.map { RequestPredicate(claim = it, privacyNote = "Only yes/no shared") },
                retentionDays = 90,
                loggedInTransparencyLog = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch request from relay", e)
            null
        }
    }

    /**
     * Holder side: build SD-JWT presentation and post to relay.
     */
    suspend fun holderRespondViaRelay(qrPayload: String) {
        val requestUri = parseRequestUri(qrPayload) ?: return
        val verifierPubKey = parseVerifierPubKey(qrPayload)
        val credentials = issuanceUseCase.getStoredCredentials().getOrNull() ?: return
        val credential = credentials.firstOrNull { !it.isRevoked && it.rawSdJwt != null } ?: return

        verificationUseCase.respondViaRelay(requestUri, credential.localId, verifierPubKey)
        Log.d(TAG, "Holder response posted to relay (encrypted=${verifierPubKey != null})")
    }

    /**
     * Verifier side: poll relay for response and verify.
     */
    suspend fun awaitVerifierResult(): CachetResult {
        val session = activeVerifierSession
            ?: return DemoFixtures.cachetResultPass

        return try {
            val result = verificationUseCase.awaitAndVerifyRelayResponse(session)
            activeVerifierSession = null
            loadActivity()

            CachetResult(
                cachetName = session.packId.replace("-", " ").replaceFirstChar { it.uppercase() },
                allPassed = result.summary?.badgeGranted ?: (result.badge != "none"),
                passedCount = result.summary?.requiredSatisfied ?: result.predicateResults.count { it.status == "satisfied" },
                totalCount = result.summary?.requiredTotal ?: result.predicateResults.size,
                predicates = result.predicateResults.map { p ->
                    PredicateResult(
                        label = p.predicateId,
                        passed = p.status == "satisfied",
                        failReason = p.reason
                    )
                },
                validityLabel = "90 days",
                cachetType = CachetType.IDENTITY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Relay verification failed", e)
            activeVerifierSession = null
            CachetResult(
                cachetName = "Verification Failed",
                allPassed = false,
                passedCount = 0,
                totalCount = 0,
                predicates = emptyList(),
                cachetType = CachetType.IDENTITY
            )
        }
    }

    private fun parseQrParam(qrPayload: String, param: String): String? {
        // cachet://verify?request_uri=http://...&vk=...
        val prefix = "$param="
        val idx = qrPayload.indexOf(prefix)
        if (idx < 0) return null
        val start = idx + prefix.length
        val end = qrPayload.indexOf('&', start)
        return if (end < 0) qrPayload.substring(start) else qrPayload.substring(start, end)
    }

    private fun parseRequestUri(qrPayload: String): String? = parseQrParam(qrPayload, "request_uri")
    private fun parseVerifierPubKey(qrPayload: String): String? = parseQrParam(qrPayload, "vk")

    // ── Existing flows ──

    private fun loadDemoCredentials() {
        viewModelScope.launch {
            val realResult = try {
                issuanceUseCase.requestSDJWTCredential(
                    clientId = "cachet-android-wallet",
                    credentialTypes = listOf("VerifiableCredential", "IdentityCredential"),
                    sessionId = "demo-session"
                )
            } catch (e: Exception) {
                Log.d(TAG, "Demo: backend unavailable, using static fixtures", e)
                null
            }

            if (realResult?.isSuccess == true) {
                Log.d(TAG, "Demo: issued real SD-JWT credential from backend")
                val credentials = issuanceUseCase.getStoredCredentials().getOrNull() ?: emptyList()
                _uiState.value = if (credentials.isEmpty()) {
                    WalletUiState.Empty
                } else {
                    WalletUiState.HasCredentials(
                        credentials = credentials.map { CredentialMapper.toCardUi(it) },
                        vaultSummary = CredentialMapper.toVaultSummary(credentials)
                    )
                }
            } else {
                Log.d(TAG, "Demo: using static fixtures")
                _uiState.value = WalletUiState.HasCredentials(
                    credentials = DemoFixtures.credentials,
                    vaultSummary = DemoFixtures.vaultSummary
                )
            }

            _activityState.value = ActivityUiState(
                historyGroups = DemoFixtures.historyGroups,
                receipts = DemoFixtures.receipts
            )
        }
    }

    fun loadCredentials() {
        viewModelScope.launch {
            Log.d(TAG, "Loading credentials...")
            _uiState.value = WalletUiState.Loading

            issuanceUseCase.getStoredCredentials()
                .onSuccess { credentials ->
                    Log.d(TAG, "Loaded ${credentials.size} credentials")
                    _uiState.value = if (credentials.isEmpty()) {
                        WalletUiState.Empty
                    } else {
                        WalletUiState.HasCredentials(
                            credentials = credentials.map { CredentialMapper.toCardUi(it) },
                            vaultSummary = CredentialMapper.toVaultSummary(credentials)
                        )
                    }
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to load credentials", exception)
                    _uiState.value = WalletUiState.Error(
                        exception.message ?: "Failed to load credentials"
                    )
                }
        }
    }

    fun loadActivity() {
        viewModelScope.launch {
            consentUseCase.getConsentReceipts()
                .onSuccess { consentReceipts ->
                    Log.d(TAG, "Loaded ${consentReceipts.size} consent receipts")
                    val timestamps = consentReceipts.associate { it.id to it.timestamp }
                    val historyEntries = consentReceipts.map { ActivityMapper.toHistoryEntry(it) }
                    val receiptItems = consentReceipts.map { ActivityMapper.toReceiptItem(it) }
                    val groups = ActivityMapper.groupByDate(historyEntries, timestamps)
                    _activityState.value = _activityState.value.copy(
                        historyGroups = groups,
                        receipts = receiptItems
                    )
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to load activity", exception)
                }
        }
    }

    fun runAudit() {
        viewModelScope.launch {
            consentUseCase.verifyAllConsentReceipts()
                .onSuccess { results ->
                    val total = results.size
                    val verified = results.values.count { it }
                    val pct = if (total > 0) (verified * 100 / total) else 100
                    _activityState.value = _activityState.value.copy(
                        auditResult = "$pct% of receipts verified in log"
                    )
                }
                .onFailure { exception ->
                    Log.e(TAG, "Audit failed", exception)
                    _activityState.value = _activityState.value.copy(
                        auditResult = "Audit failed: ${exception.message}"
                    )
                }
        }
    }

    fun startVeriffVerification() {
        if (demoMode) return
        viewModelScope.launch {
            Log.d(TAG, "Starting Veriff verification...")
            _uiState.value = WalletUiState.VerificationInProgress
            try {
                when (val result = veriffService.startVerification()) {
                    is VeriffResult.Success -> {
                        Log.d(TAG, "Veriff approved, sessionId=${result.sessionId}")
                        requestCredentialWithSession(result.sessionId)
                    }
                    is VeriffResult.Failure -> {
                        Log.e(TAG, "Veriff verification failed: ${result.reason}")
                        _uiState.value = WalletUiState.Error("Verification failed: ${result.reason}")
                    }
                    is VeriffResult.Cancelled -> {
                        Log.d(TAG, "Veriff verification cancelled")
                        loadCredentials()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during verification", e)
                _uiState.value = WalletUiState.Error("Unexpected error: ${e.message}")
            }
        }
    }

    private suspend fun requestCredentialWithSession(sessionId: String) {
        val sdJwtResult = issuanceUseCase.requestSDJWTCredential(
            clientId = "cachet-android-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential"),
            sessionId = sessionId
        )

        if (sdJwtResult.isSuccess) {
            Log.d(TAG, "SD-JWT credential issued: ${sdJwtResult.getOrNull()?.localId}")
            loadCredentials()
            return
        }

        Log.w(TAG, "SD-JWT issuance failed, falling back to legacy", sdJwtResult.exceptionOrNull())
        issuanceUseCase.requestCredential(
            clientId = "cachet-android-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential"),
            sessionId = sessionId
        ).onSuccess { credential ->
            Log.d(TAG, "Legacy credential issued: ${credential.localId}")
            loadCredentials()
        }.onFailure { exception ->
            Log.e(TAG, "Credential issuance failed", exception)
            _uiState.value = WalletUiState.Error(
                "Failed to issue credential: ${exception.message}"
            )
        }
    }

    suspend fun shareCredential(request: VerificationRequest): CachetResult {
        if (demoMode) return DemoFixtures.cachetResultPass

        val credentials = issuanceUseCase.getStoredCredentials().getOrNull() ?: emptyList()
        val credential = credentials.firstOrNull { !it.isRevoked }
            ?: return DemoFixtures.cachetResultPass

        val domainPredicates = request.predicates.map { mapPredicateToDomain(it.claim) }

        val presentationRequest = PresentationRequest(
            rpIdentifier = "cachet.verifier.local",
            rpDisplayName = "Cachet Verifier",
            purpose = request.question,
            requestedPredicates = domainPredicates,
            retentionPeriod = "P${request.retentionDays}D"
        )

        val consent = ConsentDetails(
            explicitConsent = true,
            dataMinimizationAcknowledged = true,
            retentionPeriodUnderstood = true,
            retentionPeriodDays = request.retentionDays
        )

        val result = consentUseCase.presentCredential(
            credentialId = credential.localId,
            presentationRequest = presentationRequest,
            userConsent = consent
        ).getOrNull()

        loadActivity()

        return if (result != null && result.success) {
            CachetResult(
                cachetName = request.question.removeSuffix("?").trim(),
                allPassed = true,
                passedCount = result.predicatesProven.size,
                totalCount = request.predicates.size,
                predicates = request.predicates.map { pred ->
                    PredicateResult(label = pred.claim, passed = true)
                },
                validityLabel = "${request.retentionDays} days",
                cachetType = CachetType.IDENTITY
            )
        } else {
            CachetResult(
                cachetName = "Incomplete",
                allPassed = false,
                passedCount = 0,
                totalCount = request.predicates.size,
                predicates = request.predicates.map { pred ->
                    PredicateResult(
                        label = pred.claim,
                        passed = false,
                        failReason = result?.errorMessage ?: "Credential cannot satisfy request"
                    )
                },
                cachetType = CachetType.IDENTITY
            )
        }
    }

    private fun mapPredicateToDomain(uiClaim: String): String {
        val lower = uiClaim.lowercase()
        return when {
            "age" in lower && ("18" in lower || "older" in lower) -> "age_gte_18"
            "age" in lower && "21" in lower -> "age_gte_21"
            "identity" in lower || "id verified" in lower -> "identity_verified"
            "liveness" in lower -> "liveness_verified"
            else -> uiClaim.lowercase().replace(" ", "_")
        }
    }

    fun revokeCredential(localId: String) {
        viewModelScope.launch {
            issuanceUseCase.revokeCredential(localId)
                .onSuccess {
                    loadCredentials()
                }
                .onFailure { exception ->
                    _uiState.value = WalletUiState.Error(
                        "Failed to revoke credential: ${exception.message}"
                    )
                }
        }
    }
}

sealed class WalletUiState {
    object Loading : WalletUiState()
    object Empty : WalletUiState()
    object VerificationInProgress : WalletUiState()
    data class HasCredentials(
        val credentials: List<CredentialCardUi>,
        val vaultSummary: VaultSummaryUi
    ) : WalletUiState()
    data class Error(val message: String) : WalletUiState()
}

data class ActivityUiState(
    val historyGroups: List<HistoryGroup> = emptyList(),
    val receipts: List<ReceiptItem> = emptyList(),
    val auditResult: String? = null
)
