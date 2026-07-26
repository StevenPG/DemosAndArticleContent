package com.stevenpg.specifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@DiscriminatorValue("CARGO")
public class CargoFlight extends Flight {

    @Column(name = "max_payload_kg")
    private Integer maxPayloadKg;

    @Column(name = "hazmat_certified")
    private Boolean hazmatCertified;

    protected CargoFlight() {
    }

    public CargoFlight(String flightNumber, Route route, Instant departureTime, Instant arrivalTime,
            BigDecimal basePrice, Airline airline, Aircraft aircraft, int maxPayloadKg, boolean hazmatCertified) {
        super(flightNumber, route, departureTime, arrivalTime, basePrice, airline, aircraft);
        this.maxPayloadKg = maxPayloadKg;
        this.hazmatCertified = hazmatCertified;
    }

    public Integer getMaxPayloadKg() {
        return maxPayloadKg;
    }

    public Boolean getHazmatCertified() {
        return hazmatCertified;
    }
}
