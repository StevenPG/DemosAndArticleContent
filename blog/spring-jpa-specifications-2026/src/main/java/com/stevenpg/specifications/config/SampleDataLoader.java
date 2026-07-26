package com.stevenpg.specifications.config;

import com.stevenpg.specifications.repository.FlightRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the running demo app. Tests call {@link SampleData#load} directly instead. */
@Configuration
public class SampleDataLoader {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    CommandLineRunner seed(FlightRepository flights) {
        return args -> load(flights);
    }

    @Transactional
    public void load(FlightRepository flights) {
        if (flights.count() > 0) {
            return;
        }
        SampleData.load(entityManager);
    }
}
