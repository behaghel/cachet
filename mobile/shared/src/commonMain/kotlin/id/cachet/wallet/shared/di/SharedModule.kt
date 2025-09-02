package id.cachet.wallet.shared.di

import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.usecase.IssuanceUseCase
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
            baseUrl = "http://10.0.2.2:8090" // Android emulator localhost
        )
    }
    
    // Use cases
    single { 
        IssuanceUseCase(
            credentialRepository = get(),
            openID4VCIClient = get()
        ) 
    }
}