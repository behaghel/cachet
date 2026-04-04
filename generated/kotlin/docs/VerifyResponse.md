
# VerifyResponse

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **badge** | **kotlin.String** | Badge label if granted, empty string otherwise |  |
| **predicates** | **kotlin.collections.List&lt;kotlin.String&gt;** | Predicate IDs that were satisfied |  |
| **freshness** | [**inline**](#Freshness) | Freshness status of the credentials |  |
| **predicateResults** | [**kotlin.collections.List&lt;PredicateResult&gt;**](PredicateResult.md) | Per-predicate evaluation results |  |
| **summary** | [**VerificationSummary**](VerificationSummary.md) |  |  |


<a id="Freshness"></a>
## Enum: freshness
| Name | Value |
| ---- | ----- |
| freshness | ok, stale, expired |



