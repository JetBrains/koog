package org.example.koog.spring.kotlin.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import org.example.koog.spring.kotlin.structs.Transaction
import org.example.koog.spring.kotlin.structs.UserAccountInfo
import kotlin.time.Instant
import kotlin.time.Clock

class AccountReadUtils(private val userId: String?) : ToolSet {
    @Tool
    @LLMDescription("Returns a list of transactions for the current user")
    fun getLatestTransactions(
        startDate: Instant?,
        @LLMDescription("Optional transaction status filter (if null -- all statuses will be returned)")
        status: Transaction.Status? // ? = null =>>> all statuses
    ): List<Transaction> {
        return mutableListOf()
    }

    @Tool
    @LLMDescription("Get account balance (in USD) for the current user")
    fun getAccountBalance(): @LLMDescription("Get account balance (in USD) for the current user") Int {
        return 1000000
    }

    @Tool
    @LLMDescription("Get account information for the current user: name, credentials, balance, etc.")
    fun readAccountInfo(): UserAccountInfo {
        return UserAccountInfo(
            "",
            "",
            "",
            "",
            "",
            "",
            100500,
            Clock.System.now(),
            Clock.System.now()
        )
    }
}
