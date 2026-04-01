
# PredicateDefinition

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **id** | **kotlin.String** | Predicate identifier |  |
| **claim** | **kotlin.String** | Claim field name in the credential subject |  |
| **&#x60;operator&#x60;** | [**inline**](#&#x60;Operator&#x60;) | Comparison operator |  |
| **&#x60;value&#x60;** | [**kotlin.Any**](.md) | Expected value (type depends on operator) |  |
| **issuersAccepted** | **kotlin.collections.List&lt;kotlin.String&gt;** | DID patterns of accepted issuers (supports * wildcard) |  |
| **proofType** | [**inline**](#ProofType) | Required proof type |  |
| **required** | **kotlin.Boolean** | Whether this predicate is required for badge granting |  [optional] |


<a id="`Operator`"></a>
## Enum: operator
| Name | Value |
| ---- | ----- |
| &#x60;operator&#x60; | &gt;&#x3D;, &gt;, &lt;&#x3D;, &lt;, &#x3D;&#x3D;, boolean |


<a id="ProofType"></a>
## Enum: proofType
| Name | Value |
| ---- | ----- |
| proofType | sd-jwt, vc-bbs, zk-snark |



