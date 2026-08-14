package com.stevenpg.specifications.config;

import com.stevenpg.specifications.repository.FlightRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the running demo app. Tests call {@link SampleData#load} directly instead, which is why
 * this bean is excluded from the {@code test} profile - if it also ran there, every test class
 * would start from 240 flights instead of 120.
 * <p>
 * It is a {@code @Component} implementing {@code CommandLineRunner} rather than a
 * {@code @Configuration} handing back a lambda on purpose. {@code @Transactional} is applied by a
 * proxy, and a lambda created inside the configuration class calls straight through to the target
 * instance - the proxy never sees the call, no transaction is started, and the first
 * {@code em.persist(...)} fails with {@code TransactionRequiredException}. Spring invokes
 * {@link #run} through the proxy, so the annotation takes effect here.
 */
@Component
@Profile("!test")
public class SampleDataLoader implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    private final FlightRepository flights;

    public SampleDataLoader(FlightRepository flights) {
        this.flights = flights;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (flights.count() > 0) {
            return;
        }
        SampleData.load(entityManager);
    }
}
