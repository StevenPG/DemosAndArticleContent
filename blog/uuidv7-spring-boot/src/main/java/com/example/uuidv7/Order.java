package com.example.uuidv7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * The entire UUIDv7 setup on the JPA side is the single
 * {@code @UuidGenerator(style = VERSION_7)} annotation. Hibernate generates a
 * spec-compliant UUIDv7 before insert, so the ID is available immediately
 * after persist() and JDBC batching keeps working.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    protected Order() {
    }

    public Order(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }
}
