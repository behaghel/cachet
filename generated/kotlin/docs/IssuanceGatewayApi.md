# IssuanceGatewayApi

All URIs are relative to *http://localhost:8090*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**handleVeriffWebhook**](IssuanceGatewayApi.md#handleVeriffWebhook) | **POST** /webhooks/veriff | Veriff webhook endpoint |
| [**healthCheck**](IssuanceGatewayApi.md#healthCheck) | **GET** /health | Health check endpoint |
| [**requestCredential**](IssuanceGatewayApi.md#requestCredential) | **POST** /credential | Request verifiable credential |
| [**requestToken**](IssuanceGatewayApi.md#requestToken) | **POST** /oauth/token | Request OAuth2 access token |


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

val apiInstance = IssuanceGatewayApi()
val veriffSession : VeriffSession =  // VeriffSession | 
try {
    apiInstance.handleVeriffWebhook(veriffSession)
} catch (e: ClientException) {
    println("4xx response calling IssuanceGatewayApi#handleVeriffWebhook")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling IssuanceGatewayApi#handleVeriffWebhook")
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

Returns service health status. All services expose this endpoint.

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = IssuanceGatewayApi()
try {
    val result : kotlin.String = apiInstance.healthCheck()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling IssuanceGatewayApi#healthCheck")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling IssuanceGatewayApi#healthCheck")
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

val apiInstance = IssuanceGatewayApi()
val credentialRequest : CredentialRequest =  // CredentialRequest | 
try {
    val result : CredentialResponse = apiInstance.requestCredential(credentialRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling IssuanceGatewayApi#requestCredential")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling IssuanceGatewayApi#requestCredential")
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
> TokenResponse requestToken(grantType, clientId, scope)

Request OAuth2 access token

OAuth2 client credentials flow per RFC 6749

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = IssuanceGatewayApi()
val grantType : kotlin.String = grantType_example // kotlin.String | OAuth2 grant type
val clientId : kotlin.String = clientId_example // kotlin.String | Client identifier
val scope : kotlin.String = scope_example // kotlin.String | Requested scope
try {
    val result : TokenResponse = apiInstance.requestToken(grantType, clientId, scope)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling IssuanceGatewayApi#requestToken")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling IssuanceGatewayApi#requestToken")
    e.printStackTrace()
}
```

### Parameters
| **grantType** | **kotlin.String**| OAuth2 grant type | [enum: client_credentials] |
| **clientId** | **kotlin.String**| Client identifier | |
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **scope** | **kotlin.String**| Requested scope | |

### Return type

[**TokenResponse**](TokenResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/x-www-form-urlencoded
 - **Accept**: application/json

