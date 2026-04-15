package id.cachet.wallet.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.components.VerificationDirection
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.mapper.ActivityMapper
import id.cachet.wallet.android.ui.mapper.CachPackMapper
import id.cachet.wallet.android.ui.mapper.CredentialMapper
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.android.verification.VeriffResult
import id.cachet.wallet.android.verification.VeriffService
import id.cachet.wallet.domain.model.ConsentDetails
import id.cachet.wallet.domain.model.ConsentReceipt
import id.cachet.wallet.domain.model.PresentationRequest
import id.cachet.wallet.domain.model.VerifiableCredential
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
    private val demoModeParam: Boolean = false,
    private val demoEmpty: Boolean = false
) : ViewModel() {

    private val demoMode: Boolean get() = demoModeParam || DemoFixtures.isDemoActive

    companion object {
        private const val TAG = "WalletViewModel"
    }

    private val _uiState = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _activityState = MutableStateFlow(ActivityUiState())
    val activityState: StateFlow<ActivityUiState> = _activityState.asStateFlow()

    /** Active verifier session -- set when QR overlay is shown, consumed when verification completes. */
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

    // -- Relay flow --

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
            val verifiedRequest = verificationUseCase.fetchVerificationRequest(requestUri)
            val payload = verifiedRequest.payload
            Log.d(TAG, "Request fetched from relay (verified=${verifiedRequest.isVerified}, verifier=${verifiedRequest.verifierName})")
            VerificationRequest(
                question = payload.question,
                predicates = payload.predicates.map { id ->
                    RequestPredicate(
                        claim = humanizePredicateId(id),
                        privacyNote = privacyNoteForPredicate(id)
                    )
                },
                retentionDays = 90,
                loggedInTransparencyLog = true,
                verifierName = verifiedRequest.verifierName,
                isVerifierVerified = verifiedRequest.isVerified,
                cachetType = cachetTypeForPackId(payload.packId)
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
            ?: return CachetResult(
                cachetName = "Error",
                allPassed = false, passedCount = 0, totalCount = 0,
                predicates = emptyList(),
                isError = true,
                errorMessage = "No active verifier session"
            )

        val packType = cachetTypeForPackId(session.packId)

        return try {
            val result = verificationUseCase.awaitAndVerifyRelayResponse(session)
            activeVerifierSession = null

            val allPassed = result.summary?.cachetGranted ?: result.badge.isNotEmpty()
            val outcome = if (allPassed) ConsentReceipt.OUTCOME_PASSED else ConsentReceipt.OUTCOME_INCOMPLETE

            // Generate a consent receipt so the Activity tab reflects this verification
            val predicateIds = result.predicateResults.map { it.predicateId }
            if (predicateIds.isNotEmpty()) {
                val credentials = issuanceUseCase.getStoredCredentials().getOrNull() ?: emptyList()
                val cred = credentials.firstOrNull { !it.isRevoked }?.credential
                    ?: DemoFixtures.syntheticCredential
                consentUseCase.generateConsentReceipt(
                    credential = cred,
                    presentationRequest = PresentationRequest(
                        rpIdentifier = "did:web:cachet.id:verifier",
                        rpDisplayName = "Cachet Verifier",
                        purpose = "Trust Pack verification: ${humanizePackId(session.packId)}",
                        requestedPredicates = predicateIds
                    ),
                    userConsent = ConsentDetails(
                        explicitConsent = true,
                        dataMinimizationAcknowledged = true,
                        retentionPeriodUnderstood = true
                    ),
                    outcome = outcome
                )
            }

            val cachetResult = CachetResult(
                cachetName = result.badge.ifEmpty { humanizePackId(session.packId) },
                allPassed = allPassed,
                passedCount = result.summary?.requiredSatisfied ?: result.predicateResults.count { it.status == "satisfied" },
                totalCount = result.summary?.requiredTotal ?: result.predicateResults.size,
                predicates = result.predicateResults.map { p ->
                    PredicateResult(
                        label = humanizePredicateId(p.predicateId),
                        passed = p.status == "satisfied",
                        failReason = p.reason,
                        privacyNote = privacyNoteForPredicate(p.predicateId)
                    )
                },
                validityLabel = "90 days",
                cachetType = packType
            )
            appendVerificationToActivity(cachetResult)
            cachetResult
        } catch (e: Exception) {
            Log.e(TAG, "Relay verification failed", e)
            activeVerifierSession = null
            val cachetResult = CachetResult(
                cachetName = humanizePackId(session.packId),
                allPassed = false,
                passedCount = 0,
                totalCount = 0,
                predicates = emptyList(),
                cachetType = packType,
                isError = true,
                errorMessage = e.message ?: "Verification could not be completed"
            )
            appendVerificationToActivity(cachetResult)
            cachetResult
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

    /**
     * Resolve a cachet:// deep link to a VerificationRequest.
     * In demo mode, maps the `pack` param to the fixture request.
     * In prod mode, fetches from the relay via `request_uri`.
     */
    suspend fun resolveDeepLink(uri: String): VerificationRequest? {
        if (demoMode) {
            val packParam = parseQrParam(uri, "pack") ?: return null
            val pack = DemoFixtures.cachPacks.firstOrNull { it.id.contains(packParam, ignoreCase = true) }
                ?: DemoFixtures.cachPacks.firstOrNull { it.cachetType.name.equals(packParam, ignoreCase = true) }
                ?: return null
            return CachPackMapper.toVerificationRequest(pack)
        }
        return fetchRequestFromRelay(uri)
    }

    private fun parseRequestUri(qrPayload: String): String? = parseQrParam(qrPayload, "request_uri")
    private fun parseVerifierPubKey(qrPayload: String): String? = parseQrParam(qrPayload, "vk")

    internal fun humanizePredicateId(id: String): String = when (id) {
        "age.ge.18" -> "You are 18 or older"
        "age.ge.21" -> "You are 21 or older"
        "identity.verified" -> "Your identity is verified"
        "criminal.clear.es" -> "No criminal record (ES)"
        "firstaid.valid.es" -> "First aid certificate (ES)"
        "references.verified" -> "2+ verified references"
        "liveness.verified" -> "Liveness check passed"
        else -> if (id.contains(".")) id.replace(".", " ").replaceFirstChar { it.uppercase() } else id
    }

    private fun privacyNoteForPredicate(label: String): String {
        val lower = label.lowercase()
        return when {
            "age" in lower -> "Your exact age will NOT be shared"
            "identity" in lower || "id verified" in lower -> "Your name will NOT be shared"
            "criminal" in lower -> "Only a clear/not-clear result"
            "first aid" in lower -> "Only a valid/not-valid result"
            "reference" in lower -> "Referee names will NOT be shared"
            "liveness" in lower -> "Only a pass/fail result"
            "nationality" in lower -> "Only country, not passport number"
            "name" in lower -> "Shared as-is from your credential"
            else -> "Only yes/no shared"
        }
    }

    private fun humanizePackId(id: String): String = when (id) {
        PackIds.CHILDCARE_ES -> "Childcare Ready (ES)"
        PackIds.CHILDCARE_BASE -> "Childcare Ready"
        PackIds.CHILDCARE_FR -> "Childcare Ready (FR)"
        PackIds.CHILDCARE_EE -> "Childcare Ready (EE)"
        PackIds.SAFE_SELLER -> "Safe Seller"
        PackIds.IDENTITY_BASIC -> "Identity Verified"
        else -> id.removePrefix("pack.").replace(".", " ").replaceFirstChar { it.uppercase() }
    }

    internal fun cachetTypeForPackId(id: String): CachetType = when {
        id.contains("childcare") -> CachetType.CHILDCARE
        id.contains("seller") -> CachetType.SELLER
        id.contains("age") -> CachetType.AGE
        else -> CachetType.IDENTITY
    }

    // -- Existing flows --

    fun reloadDemoData() {
        loadDemoCredentials()
    }

    private fun loadDemoCredentials() {
        Log.d(TAG, "Demo: using static fixtures")
        val creds = DemoFixtures.credentials
        _uiState.value = if (creds.isEmpty()) {
            WalletUiState.Empty
        } else {
            WalletUiState.HasCredentials(
                credentials = creds,
                vaultSummary = DemoFixtures.vaultSummary
            )
        }
        _activityState.value = ActivityUiState(
            historyGroups = DemoFixtures.historyGroups,
            receipts = DemoFixtures.receipts
        )
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

    fun appendVerificationToActivity(result: CachetResult) {
        if (demoMode) {
            val entry = HistoryEntry(
                id = "h-${System.currentTimeMillis()}",
                title = result.cachetName,
                subtitle = if (result.allPassed) "Verification passed" else "Verification incomplete",
                time = "Just now",
                proofSummary = "${result.passedCount} of ${result.totalCount} proofs checked",
                direction = VerificationDirection.GIVEN,
                status = if (result.allPassed) TrustStatus.PASSED else TrustStatus.INCOMPLETE,
                cachetEarned = if (result.allPassed) result.cachetType else null
            )
            val current = _activityState.value
            val todayGroup = current.historyGroups.firstOrNull()
            val updatedGroups = if (todayGroup != null && todayGroup.dateLabel == "TODAY") {
                listOf(todayGroup.copy(entries = listOf(entry) + todayGroup.entries)) +
                    current.historyGroups.drop(1)
            } else {
                listOf(HistoryGroup("TODAY", listOf(entry))) + current.historyGroups
            }
            _activityState.value = current.copy(historyGroups = updatedGroups)
        } else {
            loadActivity()
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

    /**
     * Empty vault → identity verification. In demo mode, simulate by loading
     * only the identity credential. In prod, delegate to Veriff.
     */
    fun startIdentityVerification() {
        if (demoMode) {
            // Use identity from active scenario, or synthesize one for empty vault
            val identity = DemoFixtures.credentials.firstOrNull { it.cachetType == CachetType.IDENTITY }
                ?: CredentialCardUi(
                    localId = "demo-identity",
                    displayName = "Identity",
                    issuerLine = "Issued by Veriff",
                    freshnessLabel = "now",
                    isRevoked = false,
                    cachetType = CachetType.IDENTITY,
                    trustStatus = TrustStatus.VERIFIED,
                    predicates = listOf("Age 18+", "ID Verified", "Liveness", "Nationality"),
                    sharesSummary = ""
                )
            _uiState.value = WalletUiState.HasCredentials(
                credentials = listOf(identity),
                vaultSummary = VaultSummaryUi(totalCount = 1, verifiedCount = 1, pendingCount = 0)
            )
            return
        }
        startVeriffVerification()
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
        val credentials = issuanceUseCase.getStoredCredentials().getOrNull() ?: emptyList()
        val credential = credentials.firstOrNull { !it.isRevoked }

        if (demoMode) {
            // Generate a real consent receipt so the Activity tab reflects the verification.
            // Use the real credential if available, otherwise a synthetic one (demo fixtures
            // populate the UI but don't store in the repository).
            val receiptCredential = credential?.credential ?: DemoFixtures.syntheticCredential
            val matchingPack = DemoFixtures.packForType(request.cachetType ?: CachetType.IDENTITY)
            val allPassed = DemoFixtures.shouldPass(request)
            val outcome = if (allPassed) ConsentReceipt.OUTCOME_PASSED else ConsentReceipt.OUTCOME_INCOMPLETE
            try {
                generateConsentReceiptForShare(receiptCredential, request, outcome)
            } catch (e: Exception) {
                Log.w(TAG, "Demo: consent receipt generation failed (non-fatal)", e)
            }
            // Build a pack-aware result via CachPackMapper so the result name,
            // predicate count, and type match the selected pack.
            val result = CachPackMapper.toCachetResult(matchingPack, allPassed)
            appendVerificationToActivity(result)
            return result
        }

        if (credential == null) return CachetResult(
            cachetName = "No Credential",
            allPassed = false,
            passedCount = 0,
            totalCount = request.predicates.size,
            predicates = request.predicates.map { PredicateResult(it.claim, false, "No credential available") },
            cachetType = request.cachetType ?: CachetType.IDENTITY
        )

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

        // Refresh activity after sharing
        loadActivity()

        val cachetResult = if (result != null && result.success) {
            CachetResult(
                cachetName = request.question.removeSuffix("?").trim(),
                allPassed = true,
                passedCount = result.predicatesProven.size,
                totalCount = request.predicates.size,
                predicates = request.predicates.map { pred ->
                    PredicateResult(label = pred.claim, passed = true)
                },
                validityLabel = "${request.retentionDays} days",
                cachetType = request.cachetType
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
                cachetType = request.cachetType
            )
        }
        appendVerificationToActivity(cachetResult)
        return cachetResult
    }

    private suspend fun generateConsentReceiptForShare(
        credential: VerifiableCredential,
        request: VerificationRequest,
        outcome: String = ConsentReceipt.OUTCOME_PASSED
    ) {
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
        consentUseCase.generateConsentReceipt(credential, presentationRequest, consent, outcome)
        if (!demoMode) loadActivity()
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

    suspend fun getDetailForCredential(localId: String): CachetDetailUi? {
        val stored = issuanceUseCase.getStoredCredentials().getOrNull()
            ?.firstOrNull { it.localId == localId } ?: return null
        val activity = consentUseCase.getConsentReceiptsByCredential(stored.credential.id)
            .getOrNull()
            ?.map { ActivityMapper.toHistoryEntry(it) }
            ?: emptyList()
        return CredentialMapper.toDetailUi(stored, activity)
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
