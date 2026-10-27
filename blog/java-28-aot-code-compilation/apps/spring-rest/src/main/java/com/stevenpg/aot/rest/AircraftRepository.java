package com.stevenpg.aot.rest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AircraftRepository extends JpaRepository<Aircraft, String> {

    List<Aircraft> findByOperatorOrderByCallsign(String operator);

    List<Aircraft> findByTypeCodeAndYearBuiltGreaterThanEqual(String typeCode, int yearBuilt);
}
