package org.example.koog.java.structs;

import kotlinx.serialization.Serializable;

@Serializable
public class OrderInfo {
    private final int orderId;
    private final ShippingMethod shippingMethod;
    private final String origin;
    private final String destination;

    public OrderInfo(int orderId, ShippingMethod shippingMethod, String origin, String destination) {
        this.orderId = orderId;
        this.shippingMethod = shippingMethod;
        this.origin = origin;
        this.destination = destination;
    }

    public int getOrderId() { return orderId; }
    public ShippingMethod getShippingMethod() { return shippingMethod; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
}
