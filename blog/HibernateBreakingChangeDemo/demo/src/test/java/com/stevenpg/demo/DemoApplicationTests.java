package com.stevenpg.demo;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DemoApplicationTests {

	@Autowired
    private ExampleRepository exampleRepository;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldSaveAndRetrieveAnEntity() {
        ExampleEntity entity = new ExampleEntity("Test Entity");
        // By manually setting the ID here, we display the behavior that changed in Hibernate as described
        // in the README. You can remove this line on a newer version of Spring Boot to allow the example to
        // function properly.
        entity.setId(1L);
        ExampleEntity savedEntity = exampleRepository.save(entity);

        Assertions.assertNotNull(savedEntity.getId());
        Optional<ExampleEntity> retrievedEntity = exampleRepository.findById(savedEntity.getId());
        Assertions.assertTrue(retrievedEntity.isPresent());
        Assertions.assertEquals("Test Entity", retrievedEntity.get().getName());;
    }

}
