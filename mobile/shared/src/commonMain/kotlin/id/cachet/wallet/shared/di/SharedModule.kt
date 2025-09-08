package id.cachet.wallet.shared.di

import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.repository.ConsentReceiptRepository
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.repository.TransparencyLogRepository
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import id.cachet.wallet.domain.usecase.ConsentUseCase
import id.cachet.wallet.network.KtorOpenID4VCIClient
import id.cachet.wallet.network.OpenID4VCIClient
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val sharedModule = module {
    
    // HTTP Client
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
        }
    }
    
    // Network clients
    single<OpenID4VCIClient> { 
        KtorOpenID4VCIClient(
            httpClient = get(),
            // Use actual computer IP for physical devices, 10.0.2.2 for emulator  
            baseUrl = "http://192.168.1.199:8090" // Local network IP for physical devices
        )
    }
    
    // Repositories
    single<ConsentReceiptRepository> { InMemoryConsentReceiptRepository() }
    single<TransparencyLogRepository> { MockTransparencyLogRepository() }
    
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