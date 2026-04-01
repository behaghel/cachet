# ReceiptsLogApi

All URIs are relative to *http://localhost:8090*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**getInclusionProof**](ReceiptsLogApi.md#getInclusionProof) | **GET** /log/proof | Get inclusion proof |
| [**getSignedTreeHead**](ReceiptsLogApi.md#getSignedTreeHead) | **GET** /log/sth | Get signed tree head |
| [**healthCheck**](ReceiptsLogApi.md#healthCheck) | **GET** /health | Health check endpoint |
| [**submitReceiptHash**](ReceiptsLogApi.md#submitReceiptHash) | **POST** /receipts/hash | Submit consent receipt hash |


<a id="getInclusionProof"></a>
# **getInclusionProof**
> InclusionProof getInclusionProof(hash)

Get inclusion proof

Returns an inclusion proof for a receipt hash

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = ReceiptsLogApi()
val hash : kotlin.String = hash_example // kotlin.String | Receipt hash to prove inclusion for
try {
    val result : InclusionProof = apiInstance.getInclusionProof(hash)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ReceiptsLogApi#getInclusionProof")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ReceiptsLogApi#getInclusionProof")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **hash** | **kotlin.String**| Receipt hash to prove inclusion for | [optional] |

### Return type

[**InclusionProof**](InclusionProof.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getSignedTreeHead"></a>
# **getSignedTreeHead**
> SignedTreeHead getSignedTreeHead()

Get signed tree head

Returns the current signed tree head of the transparency log

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = ReceiptsLogApi()
try {
    val result : SignedTreeHead = apiInstance.getSignedTreeHead()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ReceiptsLogApi#getSignedTreeHead")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ReceiptsLogApi#getSignedTreeHead")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**SignedTreeHead**](SignedTreeHead.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

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

val apiInstance = ReceiptsLogApi()
try {
    val result : kotlin.String = apiInstance.healthCheck()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ReceiptsLogApi#healthCheck")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ReceiptsLogApi#healthCheck")
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

<a id="submitReceiptHash"></a>
# **submitReceiptHash**
> ReceiptHashResponse submitReceiptHash(receiptHashRequest)

Submit consent receipt hash

Submits a salted hash of a consent receipt for anchoring

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = ReceiptsLogApi()
val receiptHashRequest : ReceiptHashRequest =  // ReceiptHashRequest | 
try {
    val result : ReceiptHashResponse = apiInstance.submitReceiptHash(receiptHashRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling ReceiptsLogApi#submitReceiptHash")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling ReceiptsLogApi#submitReceiptHash")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **receiptHashRequest** | [**ReceiptHashRequest**](ReceiptHashRequest.md)|  | |

### Return type

[**ReceiptHashResponse**](ReceiptHashResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

