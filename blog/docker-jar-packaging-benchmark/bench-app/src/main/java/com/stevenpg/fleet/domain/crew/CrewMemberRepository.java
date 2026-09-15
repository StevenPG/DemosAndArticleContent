package com.stevenpg.fleet.domain.crew;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CrewMemberRepository extends JpaRepository<CrewMember, Long> {

    Optional<CrewMember> findByCode(String code);

    Page<CrewMember> findByStatus(CrewMemberStatus status, Pageable pageable);

    List<CrewMember> findByRegionAndActiveTrue(String region);

    List<CrewMemberSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(CrewMemberStatus status);

    @Query("select avg(e.massKg) from CrewMember e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from CrewMember e")
    long totalCycles();
}
