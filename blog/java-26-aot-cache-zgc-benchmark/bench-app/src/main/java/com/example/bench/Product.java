package com.example.bench;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A minimal entity - its purpose is to pull Hibernate's full bootstrap
 * (metamodel building, schema export, proxy generation) onto the startup
 * path, which is exactly the class-loading + object-graph work the AOT
 * cache accelerates.
 */
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private String name;
    private long priceCents;

    protected Product() {
    }

    public Product(String sku, String name, long priceCents) {
        this.sku = sku;
        this.name = name;
        this.priceCents = priceCents;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public long getPriceCents() {
        return priceCents;
    }
}
