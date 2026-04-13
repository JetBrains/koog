package org.example.koog.spring.kotlin.structs

import ai.koog.agents.core.tools.annotations.LLMDescription
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable

@LLMDescription("Full information about the user's issue with their bank account")
@Serializable
data class AccountIssueSummary @JsonCreator constructor(
    @property:LLMDescription("Account number of the user in the database")
    val accountNumber: String,
    @property:LLMDescription("Username of the account holder")
    val username: String,
    @property:LLMDescription("Current account balance in US dollars")
    val currentBalance: Int,
    @property:LLMDescription("ID of the transaction related to this issue, if applicable")
    val relatedTransactionId: String,
    @property:LLMDescription("ID of the dispute if one was initiated for this issue")
    val disputeId: String,
    @property:LLMDescription("What exactly is the user's issue with their account or transaction")
    val problem: String,
    @property:LLMDescription("Was the issue already resolved?")
    val resolved: Boolean
)