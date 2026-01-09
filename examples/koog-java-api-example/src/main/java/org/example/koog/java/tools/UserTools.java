package org.example.koog.java.tools;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import org.example.koog.java.structs.Item;
import org.example.koog.java.structs.OrderInfo;
import org.example.koog.java.structs.ShippingMethod;
import org.example.koog.java.structs.UserAccount;
import org.example.koog.java.structs.*;
import org.example.koog.java.tools.utils.InMemoryStore;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Java refactor of the previous Kotlin-like implementation.
 */
public class UserTools implements ToolSet {
    private final String userId;

    public UserTools(String userId) {
        this.userId = Objects.requireNonNull(userId, "userId");
    }

    @Tool
    @LLMDescription(description = "Read and return a list of orders made by the current user")
    public List<OrderInfo> readUserOrders() {
        Map<String, List<OrderInfo>> ordersByUser = InMemoryStore.getOrdersByUser();
        List<OrderInfo> orders = ordersByUser.get(userId);
        if (orders == null) return Collections.emptyList();
        return new ArrayList<>(orders);
    }

    @Tool
    @LLMDescription(description = "Issue a refund for the specified order to the user's account")
    public void issueRefund(@LLMDescription(description = "Order identifier") int orderId) {
        Map<String, UserAccount> accounts = InMemoryStore.getAccounts();
        UserAccount account = accounts.get(userId);
        if (account != null) {
            int newBalance = account.getBalanceCents() + 1000; // flat 10 USD refund
            List<Integer> newActive = account.getActiveOrderIds().stream()
                    .filter(id -> id != orderId)
                    .collect(Collectors.toList());
            UserAccount updated = new UserAccount(account.getUserId(), newBalance, newActive);
            accounts.put(userId, updated);
        }
    }

    @Tool
    @LLMDescription(description = "Create another order with the provided items. Uses a demo user if no context is available")
    public void makeAnotherOrder(@LLMDescription(description = "Line items for the new order") List<Item> items) {
        int newId = InMemoryStore.allocateOrderId();

        Map<String, List<OrderInfo>> ordersByUser = InMemoryStore.getOrdersByUser();
        List<OrderInfo> orders = ordersByUser.get(userId);
        if (orders == null) {
            orders = new ArrayList<>();
            ordersByUser.put(userId, orders);
        }

        OrderInfo last = orders.isEmpty() ? null : orders.get(orders.size() - 1);
        String origin = last != null ? last.getOrigin() : "Warehouse A";
        String destination = last != null ? last.getDestination() : "New St 1, City";
        ShippingMethod method = last != null ? last.getShippingMethod() : ShippingMethod.DPD;

        orders.add(new OrderInfo(newId, method, origin, destination));

        Map<String, UserAccount> accounts = InMemoryStore.getAccounts();
        UserAccount acc = accounts.get(userId);
        if (acc == null) {
            acc = new UserAccount(userId, 0, Collections.emptyList());
        }
        List<Integer> updatedActive = new ArrayList<>(acc.getActiveOrderIds());
        updatedActive.add(newId);
        accounts.put(userId, new UserAccount(acc.getUserId(), acc.getBalanceCents(), updatedActive));
    }

    @Tool
    @LLMDescription(description = "Read user account info including active orders and balance")
    public UserAccount readUserAccount() {
        Map<String, UserAccount> accounts = InMemoryStore.getAccounts();
        UserAccount existing = accounts.get(userId);
        if (existing != null) return existing;

        Map<String, List<OrderInfo>> ordersByUser = InMemoryStore.getOrdersByUser();
        List<OrderInfo> orders = ordersByUser.get(userId);
        List<Integer> existingOrders = new ArrayList<>();
        if (orders != null) {
            for (OrderInfo info : orders) {
                existingOrders.add(info.getOrderId());
            }
        }
        UserAccount created = new UserAccount(userId, 0, existingOrders);
        accounts.put(userId, created);
        return created;
    }
}
