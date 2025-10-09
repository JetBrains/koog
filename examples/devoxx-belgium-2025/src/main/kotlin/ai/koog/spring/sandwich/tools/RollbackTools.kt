package ai.koog.spring.sandwich.tools

import ai.koog.spring.sandwich.structs.ShippingMethod
import ai.koog.spring.sandwich.tools.utils.InMemoryStore
import kotlinx.coroutines.delay

class RollbackTools(val userId: String) {
    // Undo of UserTools::issueRefund: remove the flat 10 USD and re-add the order to activeOrderIds
    suspend fun undoRefund(
        orderId: Int
    ) {
        delay(50)
        val account = InMemoryStore.accounts[userId]
        if (account != null) {
            val newBalance = (account.balanceCents - 1000).coerceAtLeast(0)
            val newActive = if (orderId in account.activeOrderIds) account.activeOrderIds else account.activeOrderIds + orderId
            InMemoryStore.accounts[userId] = account.copy(balanceCents = newBalance, activeOrderIds = newActive)
        }
    }

    // Undo of UserTools::makeAnotherOrder: remove the order (by id) from user's orders and active list
    suspend fun undoAnotherOrder(
        orderId: Int
    ) {
        delay(50)
        val orders = InMemoryStore.ordersByUser[userId]
        if (orders != null) {
            val idx = orders.indexOfFirst { it.orderId == orderId }
            if (idx >= 0) {
                orders.removeAt(idx)
            }
        }
        val acc = InMemoryStore.accounts[userId]
        if (acc != null) {
            InMemoryStore.accounts[userId] = acc.copy(activeOrderIds = acc.activeOrderIds.filterNot { it == orderId })
        }
    }

    // Undo counterpart of OrderTools::contactCarrier: notify carrier that previous request is cancelled
    suspend fun notifyCarrierAboutCancellation(
        shippingMethod: ShippingMethod,
        orderId: Int,
        request: String
    ): String {
        delay(50)
        return "Cancellation sent to ${shippingMethod.name} for order #$orderId. Reverting previous request='${request.take(120)}'"
    }

    // Undo of OrderTools::updateAddress: revert to previous address if known; since we don't persist history, fall back to origin/dummy previous
    suspend fun rollbackAddress(
        orderId: Int,
        address: String
    ): String {
        delay(50)
        val found = InMemoryStore.findOrder(orderId)
        if (found != null) {
            val (userId, idx) = found
            val list = InMemoryStore.ordersByUser[userId]
            if (list != null) {
                val current = list[idx]
                // we interpret 'address' as the previous address the caller provides to rollback to
                list[idx] = current.copy(destination = address)
                return "Address for order #$orderId rolled back to '$address'"
            }
        }
        return "Order #$orderId not found; nothing to rollback"
    }
}