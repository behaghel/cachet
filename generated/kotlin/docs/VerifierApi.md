# VerifierApi

All URIs are relative to *http://localhost:8090*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**healthCheck**](VerifierApi.md#healthCheck) | **GET** /health | Health check endpoint |
| [**listPacks**](VerifierApi.md#listPacks) | **GET** /packs | List available Trust Packs |
| [**verifyPresentation**](VerifierApi.md#verifyPresentation) | **POST** /presentations/verify | Verify a credential presentation |


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

val apiInstance = VerifierApi()
try {
    val result : kotlin.String = apiInstance.healthCheck()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling VerifierApi#healthCheck")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling VerifierApi#healthCheck")
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

<a id="listPacks"></a>
# **listPacks**
> kotlin.collections.List&lt;Pack&gt; listPacks()

List available Trust Packs

Returns all registered Trust Pack definitions

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = VerifierApi()
try {
    val result : kotlin.collections.List<Pack> = apiInstance.listPacks()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling VerifierApi#listPacks")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling VerifierApi#listPacks")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**kotlin.collections.List&lt;Pack&gt;**](Pack.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="verifyPresentation"></a>
# **verifyPresentation**
> VerifyResponse verifyPresentation(verifyRequest)

Verify a credential presentation

Verifies a credential bundle against a registered policy

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = VerifierApi()
val verifyRequest : VerifyRequest =  // VerifyRequest | 
try {
    val result : VerifyResponse = apiInstance.verifyPresentation(verifyRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling VerifierApi#verifyPresentation")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling VerifierApi#verifyPresentation")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **verifyRequest** | [**VerifyRequest**](VerifyRequest.md)|  | |

### Return type

[**VerifyResponse**](VerifyResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

