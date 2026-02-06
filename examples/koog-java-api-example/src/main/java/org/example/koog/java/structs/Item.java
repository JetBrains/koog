package org.example.koog.java.structs;

import ai.koog.agents.core.tools.annotations.LLMDescription;

import java.util.Objects;

@LLMDescription(description = "Basic representation of an item in an order")
public class Item {
    @LLMDescription(description = "Stock keeping unit or product identifier")
    private final String sku;

    @LLMDescription(description = "Human readable name")
    private final String name;

    @LLMDescription(description = "Quantity of the item")
    private final int quantity;

    @LLMDescription(description = "Price per single unit in cents")
    private final int priceCents;

    public Item(String sku, String name, int quantity, int priceCents) {
        this.sku = Objects.requireNonNull(sku, "sku");
        this.name = Objects.requireNonNull(name, "name");
        this.quantity = quantity;
        this.priceCents = priceCents;
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public int getPriceCents() { return priceCents; }

    @Override
    public String toString() {
        return "Item{" +
                "sku='" + sku + '\'' +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", priceCents=" + priceCents +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item)) return false;
        Item item = (Item) o;
        return quantity == item.quantity && priceCents == item.priceCents && sku.equals(item.sku) && name.equals(item.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sku, name, quantity, priceCents);
    }
}
