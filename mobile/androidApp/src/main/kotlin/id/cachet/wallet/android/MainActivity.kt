package id.cachet.wallet.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import id.cachet.wallet.android.ui.WalletApp
import id.cachet.wallet.android.ui.WalletViewModel
import id.cachet.wallet.android.ui.theme.CachetWalletTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    
    private val walletViewModel: WalletViewModel by viewModel()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Provide activity reference to ViewModel for Veriff SDK
        walletViewModel.setActivity(this)
        
        setContent {
            CachetWalletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WalletApp()
                }
            }
        }
    }
}