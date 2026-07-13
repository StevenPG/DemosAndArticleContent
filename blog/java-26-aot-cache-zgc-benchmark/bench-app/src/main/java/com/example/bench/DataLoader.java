package com.example.bench;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds a little data at every startup so the JPA write path (transaction,
 * flush, identity generation) is part of what gets trained and measured.
 */
@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner seed(ProductRepository repository) {
        return args -> {
            for (int i = 0; i < 100; i++) {
                repository.save(new Product("SKU-" + i, "Product " + i, 999L + i));
            }
        };
    }
}
