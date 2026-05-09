package com.stevenpg.ecommerce.catalog;

import com.stevenpg.ecommerce.catalog.internal.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository products;

    CatalogService(ProductRepository products) {
        this.products = products;
    }

    public List<Product> findAll() {
        return products.findAll();
    }

    public Optional<Product> findById(UUID id) {
        return products.findById(id);
    }

    public Optional<Product> findBySku(String sku) {
        return products.findBySku(sku);
    }
}
