package id.cachet.wallet.android.ui

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class WalletViewModel(
    private val issuanceUseCase: IssuanceUseCase
) : ViewModel() {
    
    companion object {
        private const val TAG = "WalletViewModel"
    }
    
    private val _uiState = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()
    
    private val veriffIntegration = VeriffIntegration()
    private var currentActivityRef: WeakReference<Activity>? = null
    
    fun setActivity(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }
    
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
            
            val activity = currentActivityRef?.get()
            if (activity == null) {
                Log.e(TAG, "No activity reference available for Veriff SDK")
                _uiState.value = WalletUiState.Error("Cannot start verification: Activity not available")
                return@launch
            }
            
            // Launch real Veriff SDK
            veriffIntegration.startVerification(activity) { result ->
                viewModelScope.launch {
                    handleVeriffResult(result)
                }
            }
        }
    }
    
    private fun handleVeriffResult(result: VeriffIntegration.VerificationResult) {
        when (result) {
            is VeriffIntegration.VerificationResult.Success -> {
                Log.d(TAG, "Veriff verification successful, requesting credential...")
                // Proceed with credential issuance
                requestCredentialAfterVerification()
            }
            is VeriffIntegration.VerificationResult.Error -> {
                Log.e(TAG, "Veriff verification failed: ${result.message}")
                _uiState.value = WalletUiState.Error("Verification failed: ${result.message}")
            }
            is VeriffIntegration.VerificationResult.Canceled -> {
                Log.d(TAG, "Veriff verification canceled by user")
                // Return to previous state
                loadCredentials()
            }
        }
    }
    
    private fun requestCredentialAfterVerification() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting credential issuance simulation...")
                // Simulate delay for verification process
                kotlinx.coroutines.delay(3000)
                
                Log.d(TAG, "Requesting credential from backend...")
                issuanceUseCase.requestCredential(
                    clientId = "cachet-android-wallet",
                    credentialTypes = listOf("VerifiableCredential", "IdentityCredential")
                ).onSuccess { credential ->
                    Log.d(TAG, "Credential issued successfully: ${credential.localId}")
                    // Reload credentials to show the new one
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
                    loadCredentials() // Refresh the list
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
    data class HasCredentials(val credentials: List<StoredCredential>) : WalletUiState()
    data class Error(val message: String) : WalletUiState()
}