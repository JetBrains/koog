package ai.koog.spring.sandwich.tools.utils

import ai.koog.spring.sandwich.structs.OrderInfo
import ai.koog.spring.sandwich.structs.ShippingMethod
import ai.koog.spring.sandwich.structs.UserAccount

/**
 * Minimal in-memory store to simulate DB and carrier interactions.
 */
object InMemoryStore {
    private var nextOrderId: Int = 1001

    // userId -> list of orders
    val ordersByUser: MutableMap<String, MutableList<OrderInfo>> = mutableMapOf(
        "user-1" to mutableListOf(
            OrderInfo(
                orderId = 1000,
                shippingMethod = ShippingMethod.DHL,
                origin = "Warehouse A",
                destination = "Old St 1, City"
            ),
        )
    )

    // userId -> account
    val accounts: MutableMap<String, UserAccount> = mutableMapOf(
        "user-1" to UserAccount(userId = "user-1", balanceCents = 0, activeOrderIds = listOf(1000))
    )

    fun findOrder(orderId: Int): Pair<String, Int>? {
        for ((userId, list) in ordersByUser) {
            val idx = list.indexOfFirst { it.orderId == orderId }
            if (idx >= 0) return userId to idx
        }
        return null
    }

    fun allocateOrderId(): Int = nextOrderId++
}