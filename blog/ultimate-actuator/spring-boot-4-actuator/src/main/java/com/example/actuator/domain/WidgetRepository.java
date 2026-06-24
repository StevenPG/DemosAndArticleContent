package com.example.actuator.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Standard Spring Data JPA repository. Every query that runs through here shows
 * up in the JPA/Hibernate and connection-pool metrics exposed by Actuator.
 */
public interface WidgetRepository extends JpaRepository<Widget, Long> {
}
