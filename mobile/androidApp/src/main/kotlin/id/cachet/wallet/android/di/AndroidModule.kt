package id.cachet.wallet.android.di

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import id.cachet.wallet.android.data.CredentialRepositoryImpl
import id.cachet.wallet.android.ui.WalletViewModel
import id.cachet.wallet.android.verification.MockVeriffService
import id.cachet.wallet.android.verification.VeriffService
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
    
    // Verification — swap MockVeriffService for real Veriff SDK later
    single<VeriffService> { MockVeriffService() }

    // ViewModels
    viewModel { params ->
        WalletViewModel(
            issuanceUseCase = get(),
            veriffService = get(),
            consentUseCase = get(),
            demoMode = params.get<Boolean>(0),
            demoEmpty = params.get<Boolean>(1)
        )
    }
}