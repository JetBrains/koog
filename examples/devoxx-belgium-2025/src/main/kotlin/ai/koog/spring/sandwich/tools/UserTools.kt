package ai.koog.spring.sandwich.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.spring.sandwich.structs.Item
import ai.koog.spring.sandwich.structs.OrderInfo
import ai.koog.spring.sandwich.structs.ShippingMethod
import ai.koog.spring.sandwich.structs.UserAccount
import ai.koog.spring.sandwich.tools.utils.InMemoryStore
import kotlinx.coroutines.delay

class UserTools(val userId: String) : ToolSet {
    @Tool
    @LLMDescription("Read and return a list of orders made by the current user")
    suspend fun readUserOrders(): List<OrderInfo> {
        // Simulate I/O latency
        delay(50)
        return InMemoryStore.ordersByUser[userId]?.toList() ?: emptyList()
    }

    @Tool
    @LLMDescription("Issue a refund for the specified order to the user's account")
    suspend fun issueRefund(
        @LLMDescription("Order identifier")
        orderId: Int
    ) {
        delay(50)
        val account = InMemoryStore.accounts[userId]
        if (account != null) {
            // For the mock: add a flat 10 USD refund and consider order inactive
            val newBalance = account.balanceCents + 1000
            val newActive = account.activeOrderIds.filterNot { it == orderId }
            InMemoryStore.accounts[userId] = account.copy(balanceCents = newBalance, activeOrderIds = newActive)
        }
    }

    @Tool
    @LLMDescription("Create another order with the provided items. Uses a demo user if no context is available")
    suspend fun makeAnotherOrder(
        @LLMDescription("Line items for the new order")
        items: List<Item>
    ) {
        delay(50)
        val newId = InMemoryStore.allocateOrderId()
        val orders = InMemoryStore.ordersByUser.getOrPut(userId) { mutableListOf() }
        // Simplify: reuse last order's origin/destination or defaults
        val last = orders.lastOrNull()
        val origin = last?.origin ?: "Warehouse A"
        val destination = last?.destination ?: "New St 1, City"
        orders.add(
            OrderInfo(
                orderId = newId,
                shippingMethod = last?.shippingMethod ?: ShippingMethod.DPD,
                origin = origin,
                destination = destination
            )
        )
        val acc = InMemoryStore.accounts.getOrPut(userId) { UserAccount(userId, 0, emptyList()) }
        InMemoryStore.accounts[userId] = acc.copy(activeOrderIds = acc.activeOrderIds + newId)
    }

    @Tool
    @LLMDescription("Read user account info including active orders and balance")
    suspend fun readUserAccount(): UserAccount {
        delay(50)
        return InMemoryStore.accounts.getOrPut(userId) {
            val existingOrders = InMemoryStore.ordersByUser[userId]?.map { it.orderId } ?: emptyList()
            UserAccount(userId = userId, balanceCents = 0, activeOrderIds = existingOrders)
        }
    }
}