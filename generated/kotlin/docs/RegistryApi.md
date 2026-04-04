# RegistryApi

All URIs are relative to *http://localhost:8090*

| Method | HTTP request | Description |
| ------------- | ------------- | ------------- |
| [**getPackDefinition**](RegistryApi.md#getPackDefinition) | **GET** /registry/packs/{packId} | Get a specific Trust Pack definition |
| [**getPolicyManifest**](RegistryApi.md#getPolicyManifest) | **GET** /policy/manifest | Get policy manifest |
| [**healthCheck**](RegistryApi.md#healthCheck) | **GET** /health | Health check endpoint |
| [**listPackDefinitions**](RegistryApi.md#listPackDefinitions) | **GET** /registry/packs | List available Trust Pack definitions |


<a id="getPackDefinition"></a>
# **getPackDefinition**
> PackDefinition getPackDefinition(packId)

Get a specific Trust Pack definition

Returns the full pack definition including predicates

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = RegistryApi()
val packId : kotlin.String = packId_example // kotlin.String | Pack identifier (e.g., pack.childcare.readiness.es)
try {
    val result : PackDefinition = apiInstance.getPackDefinition(packId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling RegistryApi#getPackDefinition")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling RegistryApi#getPackDefinition")
    e.printStackTrace()
}
```

### Parameters
| Name | Type | Description  | Notes |
| ------------- | ------------- | ------------- | ------------- |
| **packId** | **kotlin.String**| Pack identifier (e.g., pack.childcare.readiness.es) | |

### Return type

[**PackDefinition**](PackDefinition.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getPolicyManifest"></a>
# **getPolicyManifest**
> kotlin.String getPolicyManifest()

Get policy manifest

Returns the signed policy manifest in YAML format

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = RegistryApi()
try {
    val result : kotlin.String = apiInstance.getPolicyManifest()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling RegistryApi#getPolicyManifest")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling RegistryApi#getPolicyManifest")
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

val apiInstance = RegistryApi()
try {
    val result : kotlin.String = apiInstance.healthCheck()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling RegistryApi#healthCheck")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling RegistryApi#healthCheck")
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

<a id="listPackDefinitions"></a>
# **listPackDefinitions**
> kotlin.collections.List&lt;PackDefinition&gt; listPackDefinitions()

List available Trust Pack definitions

Returns all registered Trust Pack definitions with metadata

### Example
```kotlin
// Import classes:
//import id.cachet.wallet.generated.infrastructure.*
//import id.cachet.wallet.generated.models.*

val apiInstance = RegistryApi()
try {
    val result : kotlin.collections.List<PackDefinition> = apiInstance.listPackDefinitions()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling RegistryApi#listPackDefinitions")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling RegistryApi#listPackDefinitions")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**kotlin.collections.List&lt;PackDefinition&gt;**](PackDefinition.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

