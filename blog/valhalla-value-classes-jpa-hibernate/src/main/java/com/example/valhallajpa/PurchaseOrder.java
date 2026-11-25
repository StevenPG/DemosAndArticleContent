package com.example.valhallajpa;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

/**
 * An ordinary (identity) entity whose id, money and SKU are all value classes.
 * Entities themselves stay identity classes: they are mutable, managed objects.
 */
@Entity
public class PurchaseOrder {
    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private OrderId id;

    private Money total;

    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    protected PurchaseOrder() {}

    public PurchaseOrder(OrderId id, Money total, Sku sku, Customer customer) {
        this.id = id;
        this.total = total;
        this.sku = sku;
        this.customer = customer;
    }

    public OrderId getId() {
        return id;
    }

    public Money getTotal() {
        return total;
    }

    public void setTotal(Money total) {
        this.total = total;
    }

    public Sku getSku() {
        return sku;
    }

    public Customer getCustomer() {
        return customer;
    }
}
