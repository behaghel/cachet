package id.cachet.wallet.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import id.cachet.wallet.android.ui.WalletApp
import id.cachet.wallet.android.ui.theme.CachetWalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val demoMode = intent.getBooleanExtra("demo_mode", false)
        val demoEmpty = intent.getBooleanExtra("demo_empty", false)
        val demoScenario = intent.getStringExtra("demo_scenario") ?: ""
        val deepLinkUri = if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.toString()
        } else null
        setContent {
            CachetWalletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WalletApp(
                        demoMode = demoMode,
                        demoEmpty = demoEmpty,
                        demoScenario = demoScenario,
                        deepLinkUri = deepLinkUri
                    )
                }
            }
        }
    }
}
