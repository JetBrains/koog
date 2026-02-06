package org.example.koog.java.structs;

import ai.koog.agents.core.tools.annotations.LLMDescription;

@LLMDescription(description = "Full information about the user's issue with the order")
public class OrderSupportRequest {
    @LLMDescription(description = "ID of the order in the database")
    private final int orderId;

    @LLMDescription(description = "Chosen shipment method for the order")
    private final ShippingMethod shippingMethod;

    @LLMDescription(description = "Address of the origin")
    private final String originAddress;

    @LLMDescription(description = "Address where the order must be delivered")
    private final String destinationAddress;

    @LLMDescription(description = "Price of the order in US dollars")
    private final int price;

    @LLMDescription(description = "What exactly is the user's issue with the order")
    private final String problem;

    @LLMDescription(description = "Was the issue already resolved?")
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
