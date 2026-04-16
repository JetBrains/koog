package org.example.koog.spring.kotlin.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.reflect.ToolSet
import org.example.koog.spring.kotlin.structs.Transaction
import org.example.koog.spring.kotlin.structs.TransferResult

class AccountWriteUtils(private val userId: String) : ToolSet {
    @ai.koog.agents.core.tools.annotations.Tool
    @LLMDescription("Initiates a dispute for the transaction, returns disputeID")
    fun initiateDispute(transactionId: String): String {
        return "dispute-123"
    }

    @ai.koog.agents.core.tools.annotations.Tool
    @LLMDescription("Cancels the specified transaction")
    fun cancelTransaction(transactionId: String): Transaction.Status {
        return Transaction.Status.CANCELED
    }

    @ai.koog.agents.core.tools.annotations.Tool
    @LLMDescription("Transfers money to the recipient")
    fun transferMoney(
        @LLMDescription("ID of the recipient")
        recipientId: String,
        @LLMDescription("Amount in USD to be transfered")
        amount: Int
    ): @LLMDescription("Transfers money to the recipient") TransferResult {
        return TransferResult(false, "Recipient with this ID wasn't found", "transfer-123")
    }
}
