package id.cachet.wallet.android.di

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import id.cachet.wallet.android.BuildConfig
import id.cachet.wallet.android.data.CredentialRepositoryImpl
import id.cachet.wallet.android.ui.VerificationLauncher
import id.cachet.wallet.android.ui.VeriffIntegration
import id.cachet.wallet.android.ui.WalletViewModel
import id.cachet.wallet.db.WalletDatabase
import id.cachet.wallet.domain.repository.CredentialRepository
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {
    
    // Database
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = WalletDatabase.Schema,
            context = androidContext(),
            name = "wallet.db",
            callback = object : AndroidSqliteDriver.Callback(WalletDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            }
        )
    }
    
    single { WalletDatabase(get()) }
    
    // Repository
    single<CredentialRepository> {
        CredentialRepositoryImpl(get())
    }

    single<VerificationLauncher> {
        VeriffIntegration(
            backendBaseUrl = BuildConfig.ISSUANCE_BASE_URL
        )
    }

    // ViewModels
    viewModel {
        WalletViewModel(get(), get())
    }
}
