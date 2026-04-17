package org.example.koog.spring.kotlin.structs

import ai.koog.agents.core.tools.annotations.LLMDescription
import com.fasterxml.jackson.annotation.JsonCreator
import kotlinx.serialization.Serializable

@LLMDescription("Summary about what has been done to resolve the account issue")
@Serializable
data class AccountIssueSolution @JsonCreator constructor(
    @property:LLMDescription("Account number that was affected")
    val accountNumber: String,
    @property:LLMDescription("Brief summary of the actions taken to resolve the issue")
    val actionsTaken: String
)
