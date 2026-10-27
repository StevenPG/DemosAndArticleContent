package com.stevenpg.aot.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
class FleetController {

    private final AircraftRepository repo;

    FleetController(AircraftRepository repo) {
        this.repo = repo;
    }

    /** DB-bound: JPA query + Jackson serialization of ~500 rows. */
    @GetMapping("/aircraft")
    List<Aircraft> byOperator(@RequestParam String operator) {
        return repo.findByOperatorOrderByCallsign(operator);
    }

    record TypeStats(String typeCode, long count, double avgSeats, double avgAge, double maxRangeKm) {
    }

    record FleetReport(String operator, int aircraft, List<TypeStats> types, String summary) {
    }

    /** Mixed: query, then streams/grouping/formatting in the request thread. */
    @GetMapping("/report/fleet")
    List<FleetReport> fleetReport(@RequestParam(defaultValue = "2010") int builtAfter) {
        Map<String, List<Aircraft>> byOp = new TreeMap<>();
        for (String type : DataLoader.TYPES) {
            for (Aircraft a : repo.findByTypeCodeAndYearBuiltGreaterThanEqual(type, builtAfter)) {
                byOp.computeIfAbsent(a.getOperator(), k -> new java.util.ArrayList<>()).add(a);
            }
        }
        return byOp.entrySet().stream().map(e -> {
            List<TypeStats> types = e.getValue().stream()
                    .collect(Collectors.groupingBy(Aircraft::getTypeCode))
                    .entrySet().stream()
                    .map(t -> new TypeStats(t.getKey(), t.getValue().size(),
                            t.getValue().stream().mapToInt(Aircraft::getSeats).average().orElse(0),
                            t.getValue().stream().mapToInt(a -> 2026 - a.getYearBuilt()).average().orElse(0),
                            t.getValue().stream().mapToDouble(Aircraft::getMaxRangeKm).max().orElse(0)))
                    .sorted(Comparator.comparingLong(TypeStats::count).reversed())
                    .toList();
            String summary = types.stream()
                    .map(t -> "%s x%d (%.1f seats, %.1f yrs)".formatted(t.typeCode(), t.count(), t.avgSeats(), t.avgAge()))
                    .collect(Collectors.joining("; "));
            return new FleetReport(e.getKey(), e.getValue().size(), types, summary);
        }).toList();
    }
}
