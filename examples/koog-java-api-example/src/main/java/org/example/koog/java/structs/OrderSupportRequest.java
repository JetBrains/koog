package org.example.koog.java.structs;

import ai.koog.agents.core.tools.annotations.LLMDescription;

@LLMDescription("Full information about the user's issue with the order")
public class OrderSupportRequest {
    @LLMDescription("ID of the order in the database")
    private final int orderId;

    @LLMDescription("Chosen shipment method for the order")
    private final ShippingMethod shippingMethod;

    @LLMDescription("Address of the origin")
    private final String originAddress;

    @LLMDescription("Address where the order must be delivered")
    private final String destinationAddress;

    @LLMDescription("Price of the order in US dollars")
    private final int price;

    @LLMDescription("What exactly is the user's issue with the order")
    private final String problem;

    @LLMDescription("Was the issue already resolved?")
    private final boolean resolved;

    public OrderSupportRequest(int orderId,
                               ShippingMethod shippingMethod,
                               String originAddress,
                               String destinationAddress,
                               int price,
                               String problem,
                               boolean resolved) {
        this.orderId = orderId;
        this.shippingMethod = shippingMethod;
        this.originAddress = originAddress;
        this.destinationAddress = destinationAddress;
        this.price = price;
        this.problem = problem;
        this.resolved = resolved;
    }

    public int getOrderId() { return orderId; }
    public ShippingMethod getShippingMethod() { return shippingMethod; }
    public String getOriginAddress() { return originAddress; }
    public String getDestinationAddress() { return destinationAddress; }
    public int getPrice() { return price; }
    public String getProblem() { return problem; }
    public boolean isResolved() { return resolved; }

    public OrderUpdateSummary emptyUpdate() {
        return new OrderUpdateSummary(orderId, "Nothing changed");
    }
}
