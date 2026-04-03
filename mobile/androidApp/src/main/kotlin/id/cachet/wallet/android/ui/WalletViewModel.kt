package id.cachet.wallet.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.mapper.CredentialMapper
import id.cachet.wallet.android.ui.model.CredentialCardUi
import id.cachet.wallet.android.ui.model.VaultSummaryUi
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WalletViewModel(
    private val issuanceUseCase: IssuanceUseCase,
    private val demoMode: Boolean = false
) : ViewModel() {

    companion object {
        private const val TAG = "WalletViewModel"
    }

    private val _uiState = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        if (demoMode) {
            _uiState.value = WalletUiState.HasCredentials(
                credentials = DemoFixtures.credentials,
                vaultSummary = DemoFixtures.vaultSummary
            )
        } else {
            loadCredentials()
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

    fun startVeriffVerification() {
        if (demoMode) return
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
