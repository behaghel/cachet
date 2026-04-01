
# CredentialSubject

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **id** | **kotlin.String** | Subject DID |  |
| **personalData** | [**CredentialSubjectPersonalData**](CredentialSubjectPersonalData.md) |  |  [optional] |
| **verificationLevel** | [**inline**](#VerificationLevel) | Level of verification achieved |  [optional] |
| **verified** | **kotlin.Boolean** | Whether identity is verified |  [optional] |
| **verificationMethod** | **kotlin.String** | Verification method used |  [optional] |
| **verificationMetrics** | [**CredentialSubjectVerificationMetrics**](CredentialSubjectVerificationMetrics.md) |  |  [optional] |
| **evidence** | [**kotlin.collections.List&lt;CredentialSubjectEvidenceInner&gt;**](CredentialSubjectEvidenceInner.md) | Verification evidence for audit trail |  [optional] |


<a id="VerificationLevel"></a>
## Enum: verificationLevel
| Name | Value |
| ---- | ----- |
| verificationLevel | basic, standard, premium, gold |



