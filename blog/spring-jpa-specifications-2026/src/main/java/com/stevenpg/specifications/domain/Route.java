package com.stevenpg.specifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * An {@code @Embeddable} exists mostly so the guide can show how a Specification navigates
 * into one: {@code root.get(Flight_.route).get(Route_.origin)}. The metamodel generates a
 * {@code Route_} class for embeddables exactly as it does for entities.
 */
@Embeddable
public class Route {

    @Column(name = "origin", nullable = false, length = 3)
    private String origin;

    @Column(name = "destination", nullable = false, length = 3)
    private String destination;

    @Column(name = "distance_km", nullable = false)
    private int distanceKm;

    protected Route() {
    }

    public Route(String origin, String destination, int distanceKm) {
        this.origin = origin;
        this.destination = destination;
        this.distanceKm = distanceKm;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public int getDistanceKm() {
        return distanceKm;
    }
}
