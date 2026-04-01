package id.cachet.wallet.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import id.cachet.wallet.domain.usecase.VerificationResult
import id.cachet.wallet.domain.usecase.VerificationUseCase
import id.cachet.wallet.network.PackSummary
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WalletViewModel(
    private val issuanceUseCase: IssuanceUseCase,
    private val verificationUseCase: VerificationUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "WalletViewModel"
    }

    private val _uiState = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        loadCredentials()
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
                        WalletUiState.HasCredentials(credentials)
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

    fun startVeriffVerification() {
        viewModelScope.launch {
            Log.d(TAG, "Starting Veriff verification...")
            _uiState.value = WalletUiState.VerificationInProgress
            simulateCredentialIssuance()
        }
    }

    private fun simulateCredentialIssuance() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting credential issuance simulation...")
                kotlinx.coroutines.delay(3000)

                Log.d(TAG, "Requesting credential from backend...")
                issuanceUseCase.requestCredential(
                    clientId = "cachet-android-wallet",
                    credentialTypes = listOf("VerifiableCredential", "IdentityCredential")
                ).onSuccess { credential ->
                    Log.d(TAG, "Credential issued successfully: ${credential.localId}")
                    loadCredentials()
                }.onFailure { exception ->
                    Log.e(TAG, "Credential issuance failed", exception)
                    _uiState.value = WalletUiState.Error(
                        "Failed to issue credential: ${exception.message}"
                    )
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Unexpected error in credential issuance", exception)
                _uiState.value = WalletUiState.Error(
                    "Unexpected error: ${exception.message}"
                )
            }
        }
    }

    fun showPackSelection(credentialId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Loading packs for credential $credentialId...")
            _uiState.value = WalletUiState.LoadingPacks

            verificationUseCase.getAvailablePacks()
                .onSuccess { packs ->
                    Log.d(TAG, "Loaded ${packs.size} packs")
                    _uiState.value = WalletUiState.PackSelection(
                        credentialId = credentialId,
                        packs = packs
                    )
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to load packs", exception)
                    _uiState.value = WalletUiState.Error(
                        "Failed to load Trust Packs: ${exception.message}"
                    )
                }
        }
    }

    fun verifyAgainstPack(credentialId: String, packId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Verifying credential $credentialId against pack $packId...")
            _uiState.value = WalletUiState.Verifying

            verificationUseCase.verifyCredential(credentialId, packId)
                .onSuccess { result ->
                    Log.d(TAG, "Verification complete: badge=${result.badge}, granted=${result.summary?.badgeGranted}")
                    _uiState.value = WalletUiState.VerificationComplete(result)
                }
                .onFailure { exception ->
                    Log.e(TAG, "Verification failed", exception)
                    _uiState.value = WalletUiState.Error(
                        "Verification failed: ${exception.message}"
                    )
                }
        }
    }

    fun revokeCredential(localId: String) {
        viewModelScope.launch {
            issuanceUseCase.revokeCredential(localId)
                .onSuccess { loadCredentials() }
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
    object LoadingPacks : WalletUiState()
    object Verifying : WalletUiState()
    data class HasCredentials(val credentials: List<StoredCredential>) : WalletUiState()
    data class PackSelection(val credentialId: String, val packs: List<PackSummary>) : WalletUiState()
    data class VerificationComplete(val result: VerificationResult) : WalletUiState()
    data class Error(val message: String) : WalletUiState()
}
