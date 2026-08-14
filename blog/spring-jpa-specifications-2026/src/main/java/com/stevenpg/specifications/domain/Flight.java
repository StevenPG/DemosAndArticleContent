package com.stevenpg.specifications.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The root of the search domain.
 * <p>
 * Deliberately loaded with every mapping shape a Specification might have to navigate:
 * a {@code @ManyToOne} (airline, aircraft), a {@code @ManyToMany} (amenities), a
 * {@code @OneToMany} (bookings), an {@code @Embedded} value type (route), a {@code jsonb}
 * column (metadata), and single-table inheritance with two subclasses.
 * <p>
 * Every association is {@code LAZY}. That is the correct default, and it is also what makes
 * the fetch-join and count-query sections of the guide worth reading.
 */
@Entity
@Table(name = "flight")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "flight_type")
public abstract class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String flightNumber;

    @Embedded
    private Route route;

    @Column(nullable = false)
    private Instant departureTime;

    @Column(nullable = false)
    private Instant arrivalTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlightStatus status = FlightStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "airline_id", nullable = false)
    private Airline airline;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    // @BatchSize is the 2026 answer to N+1 on a to-many you cannot fetch-join because you
    // are paginating: Hibernate loads the amenities for up to 50 flights in one IN-list query
    // instead of one query per flight.
    @BatchSize(size = 50)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "flight_amenity",
            joinColumns = @JoinColumn(name = "flight_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id"))
    private Set<Amenity> amenities = new LinkedHashSet<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "flight", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Booking> bookings = new ArrayList<>();

    /**
     * Mapped to a real PostgreSQL {@code jsonb} column. Hibernate 7 handles the mapping from
     * {@code SqlTypes.JSON}; the guide shows how to reach inside it from the Criteria API with
     * {@code CriteriaBuilder.function(...)}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    protected Flight() {
    }

    protected Flight(String flightNumber, Route route, Instant departureTime, Instant arrivalTime,
            BigDecimal basePrice, Airline airline, Aircraft aircraft) {
        this.flightNumber = flightNumber;
        this.route = route;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.basePrice = basePrice;
        this.airline = airline;
        this.aircraft = aircraft;
    }

    public void addAmenity(Amenity amenity) {
        this.amenities.add(amenity);
    }

    public void addBooking(Booking booking) {
        this.bookings.add(booking);
        booking.setFlight(this);
    }

    public void putMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    public Long getId() {
        return id;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public Route getRoute() {
        return route;
    }

    public Instant getDepartureTime() {
        return departureTime;
    }

    public Instant getArrivalTime() {
        return arrivalTime;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public FlightStatus getStatus() {
        return status;
    }

    public void setStatus(FlightStatus status) {
        this.status = status;
    }

    public Airline getAirline() {
        return airline;
    }

    public Aircraft getAircraft() {
        return aircraft;
    }

    public Set<Amenity> getAmenities() {
        return amenities;
    }

    public List<Booking> getBookings() {
        return bookings;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
