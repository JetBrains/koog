package org.example.koog.java.tools;

import ai.koog.agents.snapshot.feature.RollbackToolSet;
import org.example.koog.java.structs.OrderInfo;
import org.example.koog.java.structs.ShippingMethod;
import org.example.koog.java.structs.UserAccount;
import org.example.koog.java.tools.utils.InMemoryStore;
import ai.koog.agents.snapshot.feature.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RollbackTools implements RollbackToolSet {
    private String userId;

    public RollbackTools(String userId) {
        this.userId = Objects.requireNonNull(userId, "userId");
    }

    @Reverts(toolName = "issueRefund", toolSet = UserTools.class)
    public void undoRefund(int orderId) {
        sleepSilently(50);

        var account = InMemoryStore.accounts.get(userId);
        if (account != null) {
            int newBalance = Math.max(account.getBalanceCents() - 1000, 0);

            List<Integer> newActive;
            if (account.getActiveOrderIds().contains(orderId)) {
                newActive = account.getActiveOrderIds();
            } else {
                newActive = new ArrayList<>(account.getActiveOrderIds());
                newActive.add(orderId);
            }

            InMemoryStore.accounts.put(
                    userId,
                    new UserAccount(
                            userId,
                            newBalance,
                            newActive
                    )
            );
        }
    }

    // Undo of UserTools::makeAnotherOrder: remove the order (by id) from user's orders and active list
    @Reverts(toolName = "makeAnotherOrder", toolSet = UserTools.class)
    public void undoAnotherOrder(int orderId) {
        sleepSilently(50);

        var orders = InMemoryStore.ordersByUser.get(userId);
        if (orders != null) {
            int idx = -1;
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i).getOrderId() == orderId) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                orders.remove(idx);
            }
        }

        var acc = InMemoryStore.accounts.get(userId);
        if (acc != null) {
            List<Integer> filtered = new ArrayList<>();
            for (Integer id : acc.getActiveOrderIds()) {
                if (id != orderId) {
                    filtered.add(id);
                }
            }
            InMemoryStore.accounts.put(
                    userId,
                    new UserAccount(
                            userId,
                            acc.getBalanceCents(),
                            filtered
                    )
            );
        }
    }

    // Undo counterpart of OrderTools::contactCarrier: notify carrier that previous request is cancelled
    public String notifyCarrierAboutCancellation(
            ShippingMethod shippingMethod,
            int orderId,
            String request
    ) {
        sleepSilently(50);

        String safeRequest = request == null ? "" : request;
        String truncated = safeRequest.length() > 120
                ? safeRequest.substring(0, 120)
                : safeRequest;

        return "Cancellation sent to " + shippingMethod.name() +
                " for order #" + orderId +
                ". Reverting previous request='" + truncated + "'";
    }

    // Undo of OrderTools::updateAddress: revert to previous address if known; since we don't persist
    // history, fall back to origin/dummy previous
    public String rollbackAddress(int orderId, String address) {
        sleepSilently(50);

        var found = InMemoryStore.findOrder(orderId);
        if (found != null) {
            String foundUserId = found.getUserId();
            int idx = found.getIndex();

            var list = InMemoryStore.ordersByUser.get(foundUserId);
            if (list != null && idx >= 0 && idx < list.size()) {
                var current = list.get(idx);
                // we interpret 'address' as the previous address the caller provides to rollback to
                var updated = new OrderInfo(
                        current.getOrderId(),
                        current.getShippingMethod(),
                        current.getOrigin(),
                        address
                );
                list.set(idx, updated);
                return "Address for order #" + orderId + " rolled back to '" + address + "'";
            }
        }
        return "Order #" + orderId + " not found; nothing to rollback";
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}