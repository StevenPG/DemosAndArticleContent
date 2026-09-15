package com.stevenpg.fleet.domain.weather;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, Long> {

    Optional<WeatherObservation> findByCode(String code);

    Page<WeatherObservation> findByStatus(WeatherObservationStatus status, Pageable pageable);

    List<WeatherObservation> findByRegionAndActiveTrue(String region);

    List<WeatherObservationSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(WeatherObservationStatus status);

    @Query("select avg(e.massKg) from WeatherObservation e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from WeatherObservation e")
    long totalCycles();
}
