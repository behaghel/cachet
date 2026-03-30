package id.cachet.wallet.shared.di

import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.repository.ConsentReceiptRepository
import id.cachet.wallet.domain.repository.SqlDelightConsentReceiptRepository
import id.cachet.wallet.domain.repository.TransparencyLogRepository
import id.cachet.wallet.domain.repository.HttpTransparencyLogRepository
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import id.cachet.wallet.domain.usecase.ConsentUseCase
import id.cachet.wallet.config.AppConfig
import id.cachet.wallet.db.WalletDatabase
import id.cachet.wallet.network.KtorOpenID4VCIClient
import id.cachet.wallet.network.OpenID4VCIClient
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val sharedModule = module {

    // HTTP Client with resilience: timeouts and retry
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            install(HttpTimeout) {
                requestTimeoutMillis = AppConfig.requestTimeoutMs
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 15_000L
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 2)
                exponentialDelay()
            }
        }
    }

    // Network clients — base URL from AppConfig (default: emulator localhost)
    single<OpenID4VCIClient> {
        KtorOpenID4VCIClient(
            httpClient = get(),
            baseUrl = AppConfig.baseUrl
        )
    }

    // Repositories
    single<ConsentReceiptRepository> {
        SqlDelightConsentReceiptRepository(database = get<WalletDatabase>())
    }
    single<TransparencyLogRepository> {
        HttpTransparencyLogRepository(
            baseUrl = AppConfig.baseUrl,
            httpClient = get()
        )
    }

    // Use cases
    single {
        IssuanceUseCase(
            credentialRepository = get(),
            openID4VCIClient = get()
        )
    }

    single {
        ConsentUseCase(
            credentialRepository = get(),
            consentReceiptRepository = get(),
            transparencyLogRepository = get()
        )
    }
}
