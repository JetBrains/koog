package ai.koog.agents.example.banking.tools

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.collections.filter
import kotlin.collections.joinToString
import kotlin.collections.map
import kotlin.collections.sum
import kotlin.text.split
import kotlin.text.toDoubleOrNull
import kotlin.text.toInt
import kotlin.text.trim

@Serializable
enum class TransactionCategory(val title: String) {
    FOOD_AND_DINING("Food & Dining"),
    SHOPPING("Shopping"),
    TRANSPORTATION("Transportation"),
    ENTERTAINMENT("Entertainment"),
    GROCERIES("Groceries"),
    HEALTH("Health"),
    UTILITIES("Utilities"),
    HOME_IMPROVEMENT("Home Improvement");

    companion object {
        fun fromString(value: String): TransactionCategory? {
            return TransactionCategory.entries.find { it.title == value }
        }

        fun availableCategories(): String = entries.joinToString(", ") { it.title }
    }
}

@Serializable
data class Transaction(
    val merchant: String,
    val amount: Double,
    val category: TransactionCategory,
    val date: LocalDateTime
)

val transactionAnalysisPrompt = """
Today is 2025-05-22.
Available categories for transactions: ${TransactionCategory.availableCategories()}
"""

@LLMDescription("Tools for analyzing transaction history")
class TransactionAnalysisTools : ToolSet {

    @Tool
    @LLMDescription(
        "Retrieves transactions filtered by userId, category, start date, and end date. All parameters are optional. " +
                "If no parameters are provided, all transactions are returned. " +
                "Dates should be in the format YYYY-MM-DD."
    )
    fun getTransactions(
        @LLMDescription("The ID of the user whose transactions to retrieve.")
        userId: String? = null,

        @LLMDescription("The category to filter transactions by (e.g., 'Food & Dining', 'Shopping', 'Groceries').")
        category: String? = null,

        @LLMDescription("The start date to filter transactions by, in the format YYYY-MM-DD.")
        startDate: String? = null,

        @LLMDescription("The end date to filter transactions by, in the format YYYY-MM-DD.")
        endDate: String? = null
    ): String {
        var filteredTransactions = sampleTransactions

        // Filter by userId if provided
        // For this example, we assume all transactions belong to userId=123
        if (userId != null && userId != "123") {
            return "No transactions found for user $userId."
        }

        // Filter by category if provided
        if (category != null) {
            val categoryEnum = TransactionCategory.fromString(category)
                ?: return "Invalid category: $category. Available categories: ${TransactionCategory.availableCategories()}"
            filteredTransactions = filteredTransactions.filter { it.category == categoryEnum }
        }

        // Filter by start date if provided
        if (startDate != null) {
            val startDateParts = startDate.split("-").map { it.toInt() }
            if (startDateParts.size == 3) {
                val startDateTime = LocalDateTime(startDateParts[0], startDateParts[1], startDateParts[2], 0, 0, 0, 0)
                filteredTransactions = filteredTransactions.filter { it.date >= startDateTime }
            }
        }

        // Filter by end date if provided
        if (endDate != null) {
            val endDateParts = endDate.split("-").map { it.toInt() }
            if (endDateParts.size == 3) {
                val endDateTime =
                    LocalDateTime(endDateParts[0], endDateParts[1], endDateParts[2], 23, 59, 59, 999999999)
                filteredTransactions = filteredTransactions.filter { it.date <= endDateTime }
            }
        }

        if (filteredTransactions.isEmpty()) {
            return "No transactions found matching the specified criteria."
        }

        return filteredTransactions.joinToString("\n") {
            "${it.date}: ${it.merchant} - $${it.amount} (${it.category})"
        }
    }

    @Tool
    @LLMDescription("Calculates the sum of an array of double numbers.")
    fun sumArray(
        @LLMDescription("Comma-separated list of double numbers to sum (e.g., '1.5,2.3,4.7').")
        numbers: String
    ): String {
        val numbersList = numbers.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
        val sum = numbersList.sum()
        return "Sum: $sum"
    }
}

/**
 * Example of how to use the TransactionAnalysisTools with a chat agent.
 */
fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY") ?: ApiKeyService.openAIApiKey // Your OpenAI API key

    val toolRegistry = ToolRegistry {
        tools(TransactionAnalysisTools().asTools())
    }

    val agent = AIAgent(
        executor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = bankingAssistantSystemPrompt + transactionAnalysisPrompt,
        temperature = 0.0,
        toolRegistry = toolRegistry
    )

    println("Transaction Analysis Assistant started")
    val message = "How much have I spent on restaurants this month?"
//     You can try other messages like:
//         "What's my maximum check at a restaurant this month?"
//         "How much did I spend on groceries in the first week of May?"
//         "What's my total spending on entertainment in May?"
    val result = agent.run(message)
    println(result)
}