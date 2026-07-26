package com.stevenpg.specifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "aircraft")
public class Aircraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String manufacturer;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int seatCapacity;

    @Column(nullable = false)
    private int rangeKm;

    protected Aircraft() {
    }

    public Aircraft(String manufacturer, String model, int seatCapacity, int rangeKm) {
        this.manufacturer = manufacturer;
        this.model = model;
        this.seatCapacity = seatCapacity;
        this.rangeKm = rangeKm;
    }

    public Long getId() {
        return id;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public int getSeatCapacity() {
        return seatCapacity;
    }

    public int getRangeKm() {
        return rangeKm;
    }
}
