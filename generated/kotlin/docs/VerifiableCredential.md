
# VerifiableCredential

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **id** | **kotlin.String** | Unique credential identifier |  |
| **atContext** | **kotlin.collections.List&lt;kotlin.String&gt;** | JSON-LD context |  |
| **type** | **kotlin.collections.List&lt;kotlin.String&gt;** | Credential types |  |
| **issuer** | **kotlin.String** | Credential issuer DID |  |
| **issuanceDate** | [**java.time.OffsetDateTime**](java.time.OffsetDateTime.md) | When the credential was issued |  |
| **credentialSubject** | [**VerifiableCredentialCredentialSubject**](VerifiableCredentialCredentialSubject.md) |  |  |
| **expirationDate** | [**java.time.OffsetDateTime**](java.time.OffsetDateTime.md) | When the credential expires (optional) |  [optional] |
| **credentialStatus** | [**CredentialStatus**](CredentialStatus.md) |  |  [optional] |



