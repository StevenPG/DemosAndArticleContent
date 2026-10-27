package com.stevenpg.aot.rest;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(indexes = @Index(columnList = "operator"))
public class Aircraft {

    @Id
    private String icao24;
    private String callsign;
    private String operator;
    private String typeCode;
    private int seats;
    private int yearBuilt;
    private double maxRangeKm;

    protected Aircraft() {
    }

    public Aircraft(String icao24, String callsign, String operator, String typeCode,
                    int seats, int yearBuilt, double maxRangeKm) {
        this.icao24 = icao24;
        this.callsign = callsign;
        this.operator = operator;
        this.typeCode = typeCode;
        this.seats = seats;
        this.yearBuilt = yearBuilt;
        this.maxRangeKm = maxRangeKm;
    }

    public String getIcao24() { return icao24; }
    public String getCallsign() { return callsign; }
    public String getOperator() { return operator; }
    public String getTypeCode() { return typeCode; }
    public int getSeats() { return seats; }
    public int getYearBuilt() { return yearBuilt; }
    public double getMaxRangeKm() { return maxRangeKm; }
}
