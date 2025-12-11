package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

@Serializable
data class GetTransactionsArgs(
    @property:LLMDescription("Start date in format YYYY-MM-DD")
    val startDate: String,
    @property:LLMDescription("End date in format YYYY-MM-DD")
    val endDate: String
)

object GetTransactionsTool : SimpleTool<GetTransactionsArgs>(
    argsSerializer = GetTransactionsArgs.serializer(),
    name = "get_transactions",
    description = "Get all transactions between two dates"
) {
    override suspend fun execute(args: GetTransactionsArgs): String {
        // Simulate returning transactions
        return """
            [
              {date: "${args.startDate}", amount: -100.00, description: "Grocery Store"},
              {date: "${args.startDate}", amount: +1000.00, description: "Salary Deposit"},
              {date: "${args.endDate}", amount: -500.00, description: "Rent Payment"},
              {date: "${args.endDate}", amount: -200.00, description: "Utilities"}
            ]
        """.trimIndent()
    }
}
