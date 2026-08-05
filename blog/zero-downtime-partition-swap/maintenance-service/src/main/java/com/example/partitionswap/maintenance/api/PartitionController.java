package com.example.partitionswap.maintenance.api;

import com.example.partitionswap.maintenance.swap.PartitionCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Observability for the demo: watch staging tables appear under
 * {@code /api/partitions/staging} and graduate to {@code /api/partitions}
 * roughly ten seconds after each minute boundary.
 */
@RestController
@RequestMapping("/api/partitions")
public class PartitionController {

    private final PartitionCatalog catalog;

    public PartitionController(PartitionCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<PartitionCatalog.AttachedPartition> attached() {
        return catalog.attachedPartitions();
    }

    @GetMapping("/staging")
    public List<StagingTable> staging() {
        return catalog.detachedStagingTables().stream()
                .map(w -> new StagingTable(w.tableName(), w.start().toString(), w.end().toString()))
                .toList();
    }

    public record StagingTable(String tableName, String windowStart, String windowEnd) {
    }
}
