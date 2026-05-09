package com.stevenpg.ecommerce.catalog.internal;

import com.stevenpg.ecommerce.catalog.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);
}
