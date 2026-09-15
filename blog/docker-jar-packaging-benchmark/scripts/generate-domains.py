#!/usr/bin/env python3
"""Generate the synthetic domain packages for the packaging benchmark app.

The benchmark needs an application that is *big* - a hello-world controller
packages identically no matter what you do to it, and the interesting effects
(layer caching, CDS/AOT class loading, jlink module pruning) only show up once
there are thousands of application classes and tens of thousands of framework
classes behind them.

Rather than pretend a hand-written 4,000-line codebase is interesting reading,
this script emits N domain packages that all follow the same shape:

    domain/<name>/<Name>.java            JPA entity
    domain/<name>/<Name>Status.java      enum
    domain/<name>/<Name>Repository.java  Spring Data repository
    domain/<name>/<Name>Summary.java     projection interface
    domain/<name>/<Name>Dto.java         record
    domain/<name>/<Name>Event.java       record
    domain/<name>/<Name>Mapper.java      mapper
    domain/<name>/<Name>Service.java     @Service
    domain/<name>/<Name>Controller.java  @RestController
    domain/<name>/<Name>Seeder.java      SeedContributor

The generated sources are committed to the repository - this script exists so
the shape can be changed and the whole set regenerated, not because the build
needs it. Usage:

    python3 generate-domains.py ../bench-app/src/main/java
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

PACKAGE_ROOT = "com/stevenpg/fleet"
BASE_PACKAGE = "com.stevenpg.fleet"

# (package segment, class name, url path, table name, domain-specific field)
DOMAINS = [
    ("aircraft", "Aircraft", "aircraft", "aircraft", ("tailNumber", "String", "TAIL")),
    ("engine", "Engine", "engines", "engine", ("serialNumber", "String", "ESN")),
    ("avionics", "AvionicsUnit", "avionics", "avionics_unit", ("firmwareVersion", "String", "FW")),
    ("sensor", "Sensor", "sensors", "sensor", ("channel", "String", "CH")),
    ("telemetry", "TelemetryFrame", "telemetry", "telemetry_frame", ("frameId", "String", "FRM")),
    ("maintenance", "MaintenanceTask", "maintenance", "maintenance_task", ("manualRef", "String", "AMM")),
    ("workorder", "WorkOrder", "work-orders", "work_order", ("orderNumber", "String", "WO")),
    ("technician", "Technician", "technicians", "technician", ("badgeId", "String", "BDG")),
    ("crew", "CrewMember", "crew", "crew_member", ("licenseNumber", "String", "LIC")),
    ("flightplan", "FlightPlan", "flight-plans", "flight_plan", ("callsign", "String", "CS")),
    ("waypoint", "Waypoint", "waypoints", "waypoint", ("fixName", "String", "FIX")),
    ("airport", "Airport", "airports", "airport", ("icaoCode", "String", "ICAO")),
    ("runway", "Runway", "runways", "runway", ("designator", "String", "RWY")),
    ("alert", "Alert", "alerts", "alert", ("severityLabel", "String", "SEV")),
    ("incident", "Incident", "incidents", "incident", ("reportNumber", "String", "IR")),
    ("inventory", "InventoryItem", "inventory", "inventory_item", ("binLocation", "String", "BIN")),
    ("part", "Part", "parts", "part", ("partNumber", "String", "PN")),
    ("supplier", "Supplier", "suppliers", "supplier", ("cageCode", "String", "CAGE")),
    ("certification", "Certification", "certifications", "certification", ("authority", "String", "AUTH")),
    ("inspection", "Inspection", "inspections", "inspection", ("checklistRef", "String", "CHK")),
    ("fuel", "FuelUplift", "fuel", "fuel_uplift", ("ticketNumber", "String", "TKT")),
    ("weather", "WeatherObservation", "weather", "weather_observation", ("metar", "String", "MTR")),
    ("satellite", "SatelliteLink", "satellite-links", "satellite_link", ("noradId", "String", "NRD")),
    ("groundstation", "GroundStation", "ground-stations", "ground_station", ("siteCode", "String", "SITE")),
]


def entity(pkg: str, cls: str, table: str, extra: tuple[str, str, str]) -> str:
    field, _, _ = extra
    column = "".join("_" + c.lower() if c.isupper() else c for c in field)
    return f"""package {BASE_PACKAGE}.domain.{pkg};

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "{table}")
public class {cls} {{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 64)
    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private {cls}Status status = {cls}Status.ACTIVE;

    @Size(max = 64)
    @Column(length = 64)
    private String region;

    @Size(max = 64)
    @Column(name = "{column}", length = 64)
    private String {field};

    @PositiveOrZero
    @Column(nullable = false)
    private double massKg;

    @PositiveOrZero
    @Column(nullable = false)
    private int cycles;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {{
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }}

    @PreUpdate
    void onUpdate() {{
        this.updatedAt = Instant.now();
    }}

    public Long getId() {{
        return id;
    }}

    public void setId(Long id) {{
        this.id = id;
    }}

    public String getCode() {{
        return code;
    }}

    public void setCode(String code) {{
        this.code = code;
    }}

    public String getName() {{
        return name;
    }}

    public void setName(String name) {{
        this.name = name;
    }}

    public {cls}Status getStatus() {{
        return status;
    }}

    public void setStatus({cls}Status status) {{
        this.status = status;
    }}

    public String getRegion() {{
        return region;
    }}

    public void setRegion(String region) {{
        this.region = region;
    }}

    public String get{field[0].upper()}{field[1:]}() {{
        return {field};
    }}

    public void set{field[0].upper()}{field[1:]}(String {field}) {{
        this.{field} = {field};
    }}

    public double getMassKg() {{
        return massKg;
    }}

    public void setMassKg(double massKg) {{
        this.massKg = massKg;
    }}

    public int getCycles() {{
        return cycles;
    }}

    public void setCycles(int cycles) {{
        this.cycles = cycles;
    }}

    public boolean isActive() {{
        return active;
    }}

    public void setActive(boolean active) {{
        this.active = active;
    }}

    public Instant getCreatedAt() {{
        return createdAt;
    }}

    public void setCreatedAt(Instant createdAt) {{
        this.createdAt = createdAt;
    }}

    public Instant getUpdatedAt() {{
        return updatedAt;
    }}

    public void setUpdatedAt(Instant updatedAt) {{
        this.updatedAt = updatedAt;
    }}

    @Override
    public boolean equals(Object other) {{
        if (this == other) {{
            return true;
        }}
        if (!(other instanceof {cls} that)) {{
            return false;
        }}
        return id != null && Objects.equals(id, that.id);
    }}

    @Override
    public int hashCode() {{
        return {cls}.class.hashCode();
    }}
}}
"""


def status_enum(pkg: str, cls: str) -> str:
    return f"""package {BASE_PACKAGE}.domain.{pkg};

public enum {cls}Status {{
    ACTIVE,
    DEGRADED,
    GROUNDED,
    RETIRED;

    public boolean serviceable() {{
        return this == ACTIVE || this == DEGRADED;
    }}
}}
"""


def repository(pkg: str, cls: str) -> str:
    return f"""package {BASE_PACKAGE}.domain.{pkg};

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface {cls}Repository extends JpaRepository<{cls}, Long> {{

    Optional<{cls}> findByCode(String code);

    Page<{cls}> findByStatus({cls}Status status, Pageable pageable);

    List<{cls}> findByRegionAndActiveTrue(String region);

    List<{cls}Summary> findByActiveTrue(Pageable pageable);

    long countByStatus({cls}Status status);

    @Query("select avg(e.massKg) from {cls} e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from {cls} e")
    long totalCycles();
}}
"""


def summary(pkg: str, cls: str) -> str:
    return f"""package {BASE_PACKAGE}.domain.{pkg};

/** Closed projection - Spring Data builds the select list from these getters. */
public interface {cls}Summary {{

    String getCode();

    String getName();

    {cls}Status getStatus();

    int getCycles();
}}
"""


def dto(pkg: str, cls: str, extra: tuple[str, str, str]) -> str:
    field, _, _ = extra
    return f"""package {BASE_PACKAGE}.domain.{pkg};

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record {cls}Dto(
        Long id,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 160) String name,
        {cls}Status status,
        String region,
        String {field},
        @PositiveOrZero double massKg,
        @PositiveOrZero int cycles,
        boolean active,
        Instant updatedAt) {{}}
"""


def event(pkg: str, cls: str) -> str:
    return f"""package {BASE_PACKAGE}.domain.{pkg};

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record {cls}Event(Long id, String code, {cls}Status status, Instant occurredAt) {{

    public static {cls}Event from({cls} entity) {{
        return new {cls}Event(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }}
}}
"""


def mapper(pkg: str, cls: str, extra: tuple[str, str, str]) -> str:
    field, _, _ = extra
    getter = f"get{field[0].upper()}{field[1:]}"
    setter = f"set{field[0].upper()}{field[1:]}"
    return f"""package {BASE_PACKAGE}.domain.{pkg};

public final class {cls}Mapper {{

    private {cls}Mapper() {{}}

    public static {cls}Dto toDto({cls} entity) {{
        return new {cls}Dto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.{getter}(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }}

    public static {cls} toEntity({cls}Dto dto) {{
        {cls} entity = new {cls}();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? {cls}Status.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.{setter}(dto.{field}());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }}
}}
"""


def service(pkg: str, cls: str) -> str:
    return f"""package {BASE_PACKAGE}.domain.{pkg};

import {BASE_PACKAGE}.support.NotFoundException;
import {BASE_PACKAGE}.support.PageResponse;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class {cls}Service {{

    private final {cls}Repository repository;

    public {cls}Service({cls}Repository repository) {{
        this.repository = repository;
    }}

    public PageResponse<{cls}Dto> list(int page, int size) {{
        Page<{cls}Dto> result = repository.findAll(PageRequest.of(page, size)).map({cls}Mapper::toDto);
        return PageResponse.from(result);
    }}

    public PageResponse<{cls}Dto> byStatus({cls}Status status, int page, int size) {{
        Page<{cls}Dto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map({cls}Mapper::toDto);
        return PageResponse.from(result);
    }}

    public {cls}Dto byId(Long id) {{
        return repository.findById(id)
                .map({cls}Mapper::toDto)
                .orElseThrow(() -> new NotFoundException("{cls}", id));
    }}

    public {cls}Dto byCode(String code) {{
        return repository.findByCode(code)
                .map({cls}Mapper::toDto)
                .orElseThrow(() -> new NotFoundException("{cls}", code));
    }}

    public List<{cls}Summary> summaries(int size) {{
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }}

    public Map<String, Object> stats() {{
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus({cls}Status.ACTIVE),
                "grounded", repository.countByStatus({cls}Status.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }}

    @Transactional
    public {cls}Dto create({cls}Dto dto) {{
        {cls} saved = repository.save({cls}Mapper.toEntity(dto));
        {cls}Event.from(saved);
        return {cls}Mapper.toDto(saved);
    }}

    @Transactional
    public {cls}Dto update(Long id, {cls}Dto dto) {{
        {cls} existing = repository.findById(id).orElseThrow(() -> new NotFoundException("{cls}", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return {cls}Mapper.toDto(repository.save(existing));
    }}

    @Transactional
    public void delete(Long id) {{
        repository.deleteById(id);
    }}
}}
"""


def controller(pkg: str, cls: str, path: str) -> str:
    return f"""package {BASE_PACKAGE}.domain.{pkg};

import {BASE_PACKAGE}.support.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{path}")
public class {cls}Controller {{

    private final {cls}Service service;

    public {cls}Controller({cls}Service service) {{
        this.service = service;
    }}

    @GetMapping
    public PageResponse<{cls}Dto> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {{
        return service.list(page, size);
    }}

    @GetMapping("/status/{{status}}")
    public PageResponse<{cls}Dto> byStatus(
            @PathVariable {cls}Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {{
        return service.byStatus(status, page, size);
    }}

    @GetMapping("/summaries")
    public List<{cls}Summary> summaries(@RequestParam(defaultValue = "20") int size) {{
        return service.summaries(size);
    }}

    @GetMapping("/stats")
    public Map<String, Object> stats() {{
        return service.stats();
    }}

    @GetMapping("/{{id}}")
    public {cls}Dto byId(@PathVariable Long id) {{
        return service.byId(id);
    }}

    @GetMapping("/code/{{code}}")
    public {cls}Dto byCode(@PathVariable String code) {{
        return service.byCode(code);
    }}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public {cls}Dto create(@Valid @RequestBody {cls}Dto dto) {{
        return service.create(dto);
    }}

    @PutMapping("/{{id}}")
    public {cls}Dto update(@PathVariable Long id, @Valid @RequestBody {cls}Dto dto) {{
        return service.update(id, dto);
    }}

    @DeleteMapping("/{{id}}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {{
        service.delete(id);
    }}
}}
"""


def seeder(pkg: str, cls: str, extra: tuple[str, str, str], index: int) -> str:
    field, _, prefix = extra
    setter = f"set{field[0].upper()}{field[1:]}"
    return f"""package {BASE_PACKAGE}.domain.{pkg};

import {BASE_PACKAGE}.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class {cls}Seeder implements SeedContributor {{

    private static final String[] REGIONS = {{"NAMER", "EMEA", "APAC", "LATAM"}};

    private final {cls}Repository repository;

    public {cls}Seeder({cls}Repository repository) {{
        this.repository = repository;
    }}

    @Override
    public String domain() {{
        return "{pkg}";
    }}

    @Override
    @Transactional
    public int seed(int rows) {{
        List<{cls}> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {{
            {cls} entity = new {cls}();
            entity.setCode("{prefix}-%04d".formatted(i + {index} * 1000));
            entity.setName("{cls} unit %d".formatted(i));
            entity.setStatus({cls}Status.values()[i % {cls}Status.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.{setter}("{prefix}%05d".formatted(i * 7 + {index}));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }}
        return repository.saveAll(batch).size();
    }}
}}
"""


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2

    source_root = Path(sys.argv[1]).resolve()
    domain_root = source_root / PACKAGE_ROOT / "domain"
    if domain_root.exists():
        shutil.rmtree(domain_root)

    written = 0
    for index, (pkg, cls, path, table, extra) in enumerate(DOMAINS):
        package_dir = domain_root / pkg
        package_dir.mkdir(parents=True, exist_ok=True)
        files = {
            f"{cls}.java": entity(pkg, cls, table, extra),
            f"{cls}Status.java": status_enum(pkg, cls),
            f"{cls}Repository.java": repository(pkg, cls),
            f"{cls}Summary.java": summary(pkg, cls),
            f"{cls}Dto.java": dto(pkg, cls, extra),
            f"{cls}Event.java": event(pkg, cls),
            f"{cls}Mapper.java": mapper(pkg, cls, extra),
            f"{cls}Service.java": service(pkg, cls),
            f"{cls}Controller.java": controller(pkg, cls, path),
            f"{cls}Seeder.java": seeder(pkg, cls, extra, index + 1),
        }
        for name, body in files.items():
            (package_dir / name).write_text(body)
            written += 1

    print(f"generated {written} files across {len(DOMAINS)} domains under {domain_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
