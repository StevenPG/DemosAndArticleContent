package com.stevenpg.ecommerce.inventory;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID productId;

    @Column(nullable = false)
    private int quantityOnHand;

    @Column(nullable = false)
    private int quantityReserved;

    protected InventoryItem() {}

    public InventoryItem(UUID productId, int quantityOnHand) {
        this.productId = productId;
        this.quantityOnHand = quantityOnHand;
        this.quantityReserved = 0;
    }

    public boolean reserve(int quantity) {
        if (available() < quantity) return false;
        this.quantityReserved += quantity;
        return true;
    }

    public void release(int quantity) {
        this.quantityReserved = Math.max(0, this.quantityReserved - quantity);
    }

    public void addStock(int quantity) {
        this.quantityOnHand += quantity;
    }

    public int available() {
        return quantityOnHand - quantityReserved;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public int getQuantityOnHand() { return quantityOnHand; }
    public int getQuantityReserved() { return quantityReserved; }
}
