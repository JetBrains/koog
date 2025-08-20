package ai.koog.agents.example.userpaystatus

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

private data class Payment(
    val transactionId: String,
    val customerId: String,
    val paymentAmount: Double,
    val paymentDate: String,
    val paymentStatus: String
)

private val payments = listOf(
    Payment("T1001", "C001", 125.50, "2021-10-05", "Paid"),
    Payment("T1002", "C002", 89.99, "2021-10-06", "Unpaid"),
    Payment("T1003", "C003", 120.00, "2021-10-07", "Paid"),
    Payment("T1004", "C002", 54.30, "2021-10-05", "Paid"),
    Payment("T1005", "C001", 210.20, "2021-10-08", "Pending")
)

class PaymentStatusTool : SimpleTool<PaymentStatusTool.Args>() {

    @Serializable
    data class Args(val transactionId: String) : ToolArgs

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "retrievePaymentStatus",
        description = "Get payment status of a transaction",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "transactionId",
                description = "The transaction id.",
                type = ToolParameterType.String,
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        val transaction = payments.firstOrNull { it.transactionId == args.transactionId }
        return when {
            transaction != null -> "Current state of the payment is :\n${transaction.paymentStatus}"
            else -> "Cannot find a payment status for this transaction with id ${args.transactionId}"
        }
    }
}
