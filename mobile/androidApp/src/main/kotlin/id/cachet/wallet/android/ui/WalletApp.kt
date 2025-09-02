package id.cachet.wallet.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletApp() {
    val viewModel: WalletViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = { 
                Text(
                    "Cachet Wallet",
                    style = MaterialTheme.typography.headlineMedium
                ) 
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        val currentState = uiState
        when (currentState) {
            is WalletUiState.Loading -> {
                LoadingScreen()
            }
            is WalletUiState.Empty -> {
                EmptyWalletScreen(
                    onStartVerification = { viewModel.startVeriffVerification() }
                )
            }
            is WalletUiState.HasCredentials -> {
                CredentialsScreen(
                    credentials = currentState.credentials,
                    onStartVerification = { viewModel.startVeriffVerification() },
                    onRefresh = { viewModel.loadCredentials() }
                )
            }
            is WalletUiState.Error -> {
                ErrorScreen(
                    message = currentState.message,
                    onRetry = { viewModel.loadCredentials() }
                )
            }
            is WalletUiState.VerificationInProgress -> {
                VerificationScreen()
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text("Loading wallet...")
        }
    }
}

@Composable
fun EmptyWalletScreen(
    onStartVerification: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Cachet Wallet",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "You don't have any credentials yet.\nStart by verifying your identity with Veriff.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onStartVerification,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Start Identity Verification")
        }
    }
}

@Composable 
fun VerificationScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text("Identity verification in progress...")
            Text(
                "This may take a few moments",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}