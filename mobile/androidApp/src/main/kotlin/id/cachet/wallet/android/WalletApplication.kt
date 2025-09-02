package id.cachet.wallet.android

import android.app.Application
import id.cachet.wallet.android.di.androidModule
import id.cachet.wallet.shared.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class WalletApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@WalletApplication)
            modules(sharedModule, androidModule)
        }
    }
}