package id.cachet.wallet.domain.sync

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.ConsentReceiptRepository
import id.cachet.wallet.domain.repository.TransparencyLogRepository
import id.cachet.wallet.domain.crypto.KeyManager
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.network.OpenID4VCIClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlin.time.Clock

enum class SyncStatus { IDLE, SYNCING, ERROR }

class SyncManager(
    private val connectivity: ConnectivityObserver,
    private val queueRepository: SyncQueueRepository,
    private val consentReceiptRepository: ConsentReceiptRepository,
    private val transparencyLogRepository: TransparencyLogRepository,
    private val openID4VCIClient: OpenID4VCIClient,
    private val credentialRepository: CredentialRepository,
    private val keyManager: KeyManager? = null,
    private val clock: Clock = Clock.System,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        const val MAX_RETRY_COUNT = 5
        private const val TOKEN_EXPIRY_BUFFER_MS = 300_000L // 5 minutes
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val drainMutex = Mutex()

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    /** Start observing connectivity. Call once from app startup. */
    fun start() {
        scope.launch {
            connectivity.isOnline
                .collect { online ->
                    if (online) {
                        drainQueues()
                    }
                }
        }
        scope.launch { refreshPendingCount() }
    }

    /** Manually trigger sync (e.g., pull-to-refresh). */
    suspend fun triggerSync() {
        if (connectivity.isOnline.value) {
            drainQueues()
        }
    }

    private suspend fun drainQueues() {
        if (!drainMutex.tryLock()) return // already draining
        try {
            _syncStatus.value = SyncStatus.SYNCING
            drainAnchoringQueue()
            drainIssuanceQueue()
            _syncStatus.value = SyncStatus.IDLE
        } catch (_: Exception) {
            _syncStatus.value = SyncStatus.ERROR
        } finally {
            drainMutex.unlock()
            refreshPendingCount()
        }
    }

    // ── Anchoring queue ──

    private suspend fun drainAnchoringQueue() {
        val pending = queueRepository.getPendingAnchorings()

        for (item in pending) {
            if (!connectivity.isOnline.value) break

            val receipt = consentReceiptRepository.getReceiptById(item.receiptId)
                .getOrNull() ?: run {
                // Receipt was deleted — clean up queue entry
                queueRepository.deletePendingAnchoring(item.receiptId)
                continue
            }

            // Idempotent: if already anchored, just clean up
            if (receipt.transparencyLogEntry != null) {
                queueRepository.deletePendingAnchoring(item.receiptId)
                continue
            }

            val result = tryAnchorReceipt(receipt)
            val now = clock.now().toEpochMilliseconds()

            when (result) {
                is SyncItemResult.Success -> {
                    queueRepository.deletePendingAnchoring(item.receiptId)
                }
                is SyncItemResult.Retry -> {
                    val newCount = item.retryCount + 1
                    val newStatus = if (newCount >= MAX_RETRY_COUNT) "failed" else "pending"
                    queueRepository.updatePendingAnchoringStatus(
                        receiptId = item.receiptId,
                        status = newStatus,
                        retryCount = newCount,
                        lastAttemptAt = now
                    )
                }
                is SyncItemResult.Failed, is SyncItemResult.Expired -> {
                    queueRepository.updatePendingAnchoringStatus(
                        receiptId = item.receiptId,
                        status = "failed",
                        retryCount = item.retryCount + 1,
                        lastAttemptAt = now
                    )
                }
            }
        }
    }

    private suspend fun tryAnchorReceipt(receipt: ConsentReceipt): SyncItemResult {
        return try {
            val saltHash = receipt.salt?.let { sha256Hash(it) }
                ?: return SyncItemResult.Failed("Receipt has no salt")
            val receiptHash = receipt.receiptHash
                ?: return SyncItemResult.Failed("Receipt has no hash")

            val request = AddEntryRequest(
                receiptHash = receiptHash,
                saltHash = saltHash,
                policyId = "consent-receipt-v1"
            )

            val response = transparencyLogRepository.submitReceiptHash(request).getOrElse {
                return SyncItemResult.Retry(it.message ?: "Network error")
            }

            val transparencyEntry = TransparencyLogEntry(
                logId = response.sct.logId,
                logIndex = -1,
                sct = response.sct,
                anchoredAt = clock.now(),
                isVerified = false
            )

            consentReceiptRepository.updateTransparencyLog(
                receiptId = receipt.id,
                transparencyLogJson = json.encodeToString(TransparencyLogEntry.serializer(), transparencyEntry)
            )

            SyncItemResult.Success
        } catch (e: Exception) {
            SyncItemResult.Retry(e.message ?: "Unknown error")
        }
    }

    // ── Issuance queue ──

    private suspend fun drainIssuanceQueue() {
        // Clean up expired tokens first
        queueRepository.deleteExpiredIssuances(clock.now().toEpochMilliseconds())

        val pending = queueRepository.getPendingIssuances()

        for (item in pending) {
            if (!connectivity.isOnline.value) break

            val now = clock.now().toEpochMilliseconds()

            // Check token expiry with buffer
            if (item.tokenExpiresAt - TOKEN_EXPIRY_BUFFER_MS < now) {
                queueRepository.updatePendingIssuanceStatus(
                    id = item.id,
                    status = "expired",
                    retryCount = item.retryCount,
                    lastAttemptAt = now
                )
                continue
            }

            val result = tryResumeIssuance(item)

            when (result) {
                is SyncItemResult.Success -> {
                    queueRepository.deletePendingIssuance(item.id)
                }
                is SyncItemResult.Expired -> {
                    queueRepository.updatePendingIssuanceStatus(
                        id = item.id,
                        status = "expired",
                        retryCount = item.retryCount + 1,
                        lastAttemptAt = now
                    )
                }
                is SyncItemResult.Retry -> {
                    val newCount = item.retryCount + 1
                    val newStatus = if (newCount >= MAX_RETRY_COUNT) "failed" else "pending"
                    queueRepository.updatePendingIssuanceStatus(
                        id = item.id,
                        status = newStatus,
                        retryCount = newCount,
                        lastAttemptAt = now
                    )
                }
                is SyncItemResult.Failed -> {
                    queueRepository.updatePendingIssuanceStatus(
                        id = item.id,
                        status = "failed",
                        retryCount = item.retryCount + 1,
                        lastAttemptAt = now
                    )
                }
            }
        }
    }

    private suspend fun tryResumeIssuance(
        item: SyncQueueRepository.PendingIssuanceItem
    ): SyncItemResult {
        return try {
            val types: List<String> = json.decodeFromString(item.credentialTypesJson)

            if (item.format == "vc+sd-jwt") {
                // SD-JWT credential: needs fresh c_nonce + proof JWT
                val cNonce = try {
                    openID4VCIClient.requestNonce().cNonce
                } catch (_: Exception) {
                    null
                }

                val proofJWT = if (cNonce != null && item.keyAlias != null && keyManager != null) {
                    id.cachet.wallet.domain.crypto.KBJWTBuilder.buildProofJWT(
                        nonce = cNonce,
                        audience = id.cachet.wallet.config.AppConfig.baseUrl,
                        keyManager = keyManager,
                        keyAlias = item.keyAlias
                    )
                } else null

                val credentialResponse = openID4VCIClient.requestSDJWTCredential(
                    accessToken = item.accessToken,
                    types = types,
                    holderJWK = item.holderJwk ?: "",
                    proofJWT = proofJWT
                )

                val parsed = id.cachet.wallet.domain.crypto.SDJWTParser.parse(credentialResponse.credential)
                val displayCredential = buildDisplayCredentialFromSDJWT(parsed, types)

                val storedCredential = StoredCredential(
                    localId = generateUuid(),
                    credential = displayCredential,
                    rawSdJwt = credentialResponse.credential,
                    keyAlias = item.keyAlias,
                    createdAt = clock.now(),
                    isRevoked = false
                )

                credentialRepository.storeCredential(storedCredential)
                SyncItemResult.Success
            } else {
                // Legacy JWT credential
                val credentialResponse = openID4VCIClient.requestCredential(
                    accessToken = item.accessToken,
                    format = item.format,
                    types = types
                )

                val storedCredential = StoredCredential(
                    localId = generateUuid(),
                    credential = credentialResponse.credential,
                    rawJwt = null,
                    createdAt = clock.now(),
                    isRevoked = false
                )

                credentialRepository.storeCredential(storedCredential)
                SyncItemResult.Success
            }
        } catch (e: Exception) {
            val message = e.message ?: ""
            if (message.contains("401") || message.contains("Unauthorized", ignoreCase = true)) {
                SyncItemResult.Expired("Token rejected: $message")
            } else {
                SyncItemResult.Retry(message)
            }
        }
    }

    private fun buildDisplayCredentialFromSDJWT(
        parsed: id.cachet.wallet.domain.crypto.SDJWTParser.ParsedSDJWT,
        types: List<String>
    ): VerifiableCredential {
        val ageClaim = parsed.claims["age"]
        val age = ageClaim?.toString()?.toDoubleOrNull()?.toInt()
        val nationality = parsed.claims["nationality"]?.toString()?.removeSurrounding("\"")
        val documentType = parsed.claims["documentType"]?.toString()?.removeSurrounding("\"")

        return VerifiableCredential(
            id = "urn:sd-jwt:${generateUuid()}",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = types,
            issuer = "did:veriff:production",
            issuanceDate = clock.now().toString(),
            credentialSubject = CredentialSubject(
                id = "did:example:holder",
                verified = true,
                personalData = PersonalData(
                    age = age,
                    nationality = nationality,
                    documentType = documentType
                )
            )
        )
    }

    private fun generateUuid(): String {
        val random = kotlin.random.Random.Default
        return "${random.nextInt().toString(16)}-${random.nextInt().toString(16)}-${random.nextInt().toString(16)}-${random.nextInt().toString(16)}"
    }

    internal suspend fun refreshPendingCount() {
        val anchoringCount = queueRepository.getPendingAnchoringCount()
        val issuanceCount = queueRepository.getPendingIssuanceCount()
        _pendingCount.value = anchoringCount + issuanceCount
    }
}

/** Outcome of a single sync item attempt. */
sealed class SyncItemResult {
    object Success : SyncItemResult()
    data class Retry(val reason: String) : SyncItemResult()
    data class Expired(val reason: String) : SyncItemResult()
    data class Failed(val reason: String) : SyncItemResult()
}
