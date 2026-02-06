package org.example.koog.java.tools.utils;

import org.example.koog.java.structs.OrderInfo;
import org.example.koog.java.structs.ShippingMethod;
import org.example.koog.java.structs.UserAccount;
import org.example.koog.java.structs.*;

import java.util.*;

/**
 * Minimal in-memory store to simulate DB and carrier interactions.
 */
public final class InMemoryStore {

    private static int nextOrderId = 1001;

    // userId -> list of orders
    public static final Map<String, List<OrderInfo>> ordersByUser = new HashMap<>();
    // userId -> account
    public static final Map<String, UserAccount> accounts = new HashMap<>();

    static {
        // Initialize demo data equivalent to the Kotlin object
        List<OrderInfo> user1Orders = new ArrayList<>();
        user1Orders.add(new OrderInfo(
                1000,
                ShippingMethod.DHL,
                "Warehouse A",
                "Old St 1, City"
        ));
        ordersByUser.put("user-1", user1Orders);

        accounts.put(
                "user-1",
                new UserAccount("user-1", 0, Collections.singletonList(1000))
        );
    }

    private InMemoryStore() {
        // utility class
    }

    public static Map<String, List<OrderInfo>> getOrdersByUser() {
        return ordersByUser;
    }

    public static Map<String, UserAccount> getAccounts() {
        return accounts;
    }

    /**
     * Find an order by its id across all users.
     *
     * @return a result containing userId and index in that user's list, or {@code null} if not found.
     */
    public static FindOrderResult findOrder(int orderId) {
        for (Map.Entry<String, List<OrderInfo>> entry : ordersByUser.entrySet()) {
            String userId = entry.getKey();
            List<OrderInfo> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getOrderId() == orderId) {
                    return new FindOrderResult(userId, i);
                }
            }
        }
        return null;
    }

    public static synchronized int allocateOrderId() {
        return nextOrderId++;
    }

    /**
     * Java equivalent for the Kotlin Pair<String, Int> used in findOrder.
     */
    public static final class FindOrderResult {
        private final String userId;
        private final int index;

        public FindOrderResult(String userId, int index) {
            this.userId = userId;
            this.index = index;
        }

        public String getUserId() {
            return userId;
        }

        public int getIndex() {
            return index;
        }
    }
}