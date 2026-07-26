package com.stevenpg.specifications.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@DiscriminatorValue("PASSENGER")
public class PassengerFlight extends Flight {

    @Column(name = "seats_available")
    private Integer seatsAvailable;

    /**
     * An {@code @ElementCollection} of enums, so the guide can demonstrate the collection
     * predicates ({@code isMember}, {@code isNotEmpty}) and joining into a collection table.
     */
    @ElementCollection(targetClass = CabinClass.class)
    @CollectionTable(name = "flight_cabin_class", joinColumns = @JoinColumn(name = "flight_id"))
    @Column(name = "cabin_class", length = 32)
    @Enumerated(EnumType.STRING)
    private Set<CabinClass> cabinClasses = EnumSet.noneOf(CabinClass.class);

    protected PassengerFlight() {
    }

    public PassengerFlight(String flightNumber, Route route, Instant departureTime, Instant arrivalTime,
            BigDecimal basePrice, Airline airline, Aircraft aircraft, int seatsAvailable) {
        super(flightNumber, route, departureTime, arrivalTime, basePrice, airline, aircraft);
        this.seatsAvailable = seatsAvailable;
    }

    public void addCabinClass(CabinClass cabinClass) {
        this.cabinClasses.add(cabinClass);
    }

    public Integer getSeatsAvailable() {
        return seatsAvailable;
    }

    public void setSeatsAvailable(Integer seatsAvailable) {
        this.seatsAvailable = seatsAvailable;
    }

    public Set<CabinClass> getCabinClasses() {
        return cabinClasses;
    }
}
