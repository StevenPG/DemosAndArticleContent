package com.stevenpg.specifications.repository;

import com.stevenpg.specifications.domain.Flight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * The whole opt-in is {@link JpaSpecificationExecutor}. Extending it adds the
 * {@code findAll(Specification, ...)}, {@code count}, {@code exists}, {@code update},
 * {@code delete} and fluent {@code findBy} overloads without you writing a line of
 * implementation.
 */
public interface FlightRepository extends JpaRepository<Flight, Long>, JpaSpecificationExecutor<Flight> {
}
