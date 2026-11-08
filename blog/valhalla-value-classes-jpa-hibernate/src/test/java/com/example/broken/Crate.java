package com.example.broken;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Crate {
    @Id
    private Long id;

    private DimensionsWithInstantiator dims;

    protected Crate() {}

    public Crate(Long id, DimensionsWithInstantiator dims) {
        this.id = id;
        this.dims = dims;
    }

    public DimensionsWithInstantiator getDims() {
        return dims;
    }
}
