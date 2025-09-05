# DefaultApi

All URIs are relative to *http://localhost:8090*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**handleVeriffWebhook**](DefaultApi.md#handleVeriffWebhook) | **POST** /webhooks/veriff | Veriff webhook endpoint |
| [**healthCheck**](DefaultApi.md#healthCheck) | **GET** /healthz | Health check endpoint |
| [**requestCredential**](DefaultApi.md#requestCredential) | **POST** /credential | Request verifiable credential |
| [**requestToken**](DefaultApi.md#requestToken) | **POST** /oauth/token | Request OAuth2 access token |


<a id="handleVeriffWebhook"></a>
# **handleVeriffWebhook**
> handleVeriffWebhook(veriffSession)

Veriff webhook endpoint

Receives verification status updates from Veriff

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = DefaultApi()
val veriffSession : VeriffSession =  // VeriffSession | 
try {
    apiInstance.handleVeriffWebhook(veriffSession)
} catch (e: ClientException) {
    println("4xx response calling DefaultApi#handleVeriffWebhook")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling DefaultApi#handleVeriffWebhook")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **veriffSession** | [**VeriffSession**](VeriffSession.md)|  | |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: Not defined

<a id="healthCheck"></a>
# **healthCheck**
> kotlin.String healthCheck()

Health check endpoint

Returns service health status

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = DefaultApi()
try {
    val result : kotlin.String = apiInstance.healthCheck()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling DefaultApi#healthCheck")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling DefaultApi#healthCheck")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

**kotlin.String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: text/plain

<a id="requestCredential"></a>
# **requestCredential**
> CredentialResponse requestCredential(credentialRequest)

Request verifiable credential

Issue a verifiable credential using OpenID4VCI protocol

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = DefaultApi()
val credentialRequest : CredentialRequest =  // CredentialRequest | 
try {
    val result : CredentialResponse = apiInstance.requestCredential(credentialRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling DefaultApi#requestCredential")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling DefaultApi#requestCredential")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **credentialRequest** | [**CredentialRequest**](CredentialRequest.md)|  | |

### Return type

[**CredentialResponse**](CredentialResponse.md)

### Authorization


Configure bearerAuth:
    ApiClient.accessToken = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="requestToken"></a>
# **requestToken**
> TokenResponse requestToken(tokenRequest)

Request OAuth2 access token

OAuth2 client credentials flow for obtaining access tokens

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = DefaultApi()
val tokenRequest : TokenRequest =  // TokenRequest | 
try {
    val result : TokenResponse = apiInstance.requestToken(tokenRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling DefaultApi#requestToken")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling DefaultApi#requestToken")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **tokenRequest** | [**TokenRequest**](TokenRequest.md)|  | |

### Return type

[**TokenResponse**](TokenResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

