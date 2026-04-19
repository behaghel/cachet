package id.cachet.wallet.shared.di

import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.repository.ConsentReceiptRepository
import id.cachet.wallet.domain.repository.SqlDelightConsentReceiptRepository
import id.cachet.wallet.domain.repository.TransparencyLogRepository
import id.cachet.wallet.domain.repository.HttpTransparencyLogRepository
import id.cachet.wallet.domain.repository.PackDefinitionRepository
import id.cachet.wallet.domain.repository.SqlDelightPackDefinitionRepository
import id.cachet.wallet.domain.repository.DIDDocumentRepository
import id.cachet.wallet.domain.repository.SqlDelightDIDDocumentRepository
import id.cachet.wallet.domain.repository.StatusListRepository
import id.cachet.wallet.domain.repository.SqlDelightStatusListRepository
import id.cachet.wallet.domain.crypto.DIDResolver
import id.cachet.wallet.domain.cache.BundledPackLoader
import id.cachet.wallet.domain.cache.CachedDIDResolver
import id.cachet.wallet.domain.cache.PackDefinitionCache
import id.cachet.wallet.domain.cache.StatusListCache
import id.cachet.wallet.domain.verification.KBJWTVerifier
import id.cachet.wallet.domain.verification.LocalVerifier
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import id.cachet.wallet.domain.usecase.ConsentUseCase
import id.cachet.wallet.domain.usecase.VerificationUseCase
import id.cachet.wallet.domain.crypto.EphemeralKeyGenerator
import id.cachet.wallet.domain.transport.LocalSessionManager
import id.cachet.wallet.domain.transport.QrDirectTransport
import id.cachet.wallet.config.AppConfig
import id.cachet.wallet.db.WalletDatabase
import id.cachet.wallet.network.KtorOpenID4VCIClient
import id.cachet.wallet.network.KtorRelayClient
import id.cachet.wallet.network.KtorVerifierClient
import id.cachet.wallet.network.OpenID4VCIClient
import id.cachet.wallet.network.RelayClient
import id.cachet.wallet.network.VerifierClient
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

    // Network clients — base URLs from AppConfig (default: emulator localhost)
    single<OpenID4VCIClient> {
        KtorOpenID4VCIClient(
            httpClient = get(),
            baseUrl = AppConfig.baseUrl
        )
    }
    single<VerifierClient> {
        KtorVerifierClient(
            httpClient = get(),
            baseUrl = AppConfig.verifierUrl
        )
    }
    single<RelayClient> {
        KtorRelayClient(
            httpClient = get(),
            baseUrl = AppConfig.relayUrl
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
    single<PackDefinitionRepository> {
        SqlDelightPackDefinitionRepository(database = get<WalletDatabase>())
    }
    single<DIDDocumentRepository> {
        SqlDelightDIDDocumentRepository(database = get<WalletDatabase>())
    }
    single<StatusListRepository> {
        SqlDelightStatusListRepository(database = get<WalletDatabase>())
    }

    // KeyManager is provided by the platform-specific module (e.g. androidModule)

    // DID resolution — cached decorator with 24h TTL over HTTP resolver
    single { DIDResolver(httpClient = get()) }
    single { CachedDIDResolver(delegate = get(), repository = get()) }

    // Offline caches
    // BundledPackLoader is provided by the platform-specific module
    single {
        PackDefinitionCache(
            repository = get(),
            loadBundled = { get<BundledPackLoader>().loadBundledPacks() }
        )
    }
    single {
        StatusListCache(
            repository = get(),
            httpClient = get()
        )
    }

    // Use cases
    single {
        IssuanceUseCase(
            credentialRepository = get(),
            openID4VCIClient = get(),
            keyManager = get()
        )
    }

    single {
        ConsentUseCase(
            credentialRepository = get(),
            consentReceiptRepository = get(),
            transparencyLogRepository = get()
        )
    }

    // Local SD-JWT verifier for offline verification
    single {
        LocalVerifier(
            cachedDIDResolver = get(),
            statusListCache = get()
        )
    }

    // Proximity transport (offline, QR-direct)
    // EphemeralKeyGenerator is provided by platform-specific module (e.g., AndroidEphemeralKeyGenerator)
    single { LocalSessionManager(keyGenerator = get()) }
    single { QrDirectTransport(sessionManager = get()) }

    single {
        VerificationUseCase(
            credentialRepository = get(),
            verifierClient = get(),
            relayClient = get(),
            consentUseCase = get(),
            keyManager = get(),
            didResolver = get(),
            localVerifier = get(),
            packDefinitionCache = get(),
            proximityTransport = get()
        )
    }
}
