package com.example.partitionswap.repository;

import com.example.partitionswap.domain.IngestEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Write-side repository: the producer only ever inserts through this. */
public interface IngestEventRepository extends JpaRepository<IngestEvent, Long> {
}
