package com.stevenpg.specifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(nullable = false)
    private String passengerName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal farePaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoyaltyTier loyaltyTier = LoyaltyTier.NONE;

    @Column(nullable = false)
    private Instant bookedAt;

    protected Booking() {
    }

    public Booking(String passengerName, BigDecimal farePaid, LoyaltyTier loyaltyTier, Instant bookedAt) {
        this.passengerName = passengerName;
        this.farePaid = farePaid;
        this.loyaltyTier = loyaltyTier;
        this.bookedAt = bookedAt;
    }

    public Long getId() {
        return id;
    }

    public Flight getFlight() {
        return flight;
    }

    void setFlight(Flight flight) {
        this.flight = flight;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public BigDecimal getFarePaid() {
        return farePaid;
    }

    public LoyaltyTier getLoyaltyTier() {
        return loyaltyTier;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }
}
