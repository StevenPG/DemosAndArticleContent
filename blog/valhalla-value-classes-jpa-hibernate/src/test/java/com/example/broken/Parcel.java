package com.example.broken;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Parcel {
    @Id
    private Long id;

    private Dimensions dims;

    protected Parcel() {}

    public Parcel(Long id, Dimensions dims) {
        this.id = id;
        this.dims = dims;
    }

    public Dimensions getDims() {
        return dims;
    }
}
