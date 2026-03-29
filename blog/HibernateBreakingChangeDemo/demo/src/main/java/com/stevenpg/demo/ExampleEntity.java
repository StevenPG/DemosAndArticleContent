package com.stevenpg.demo;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "example")
public class ExampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public ExampleEntity() {
    }

    public ExampleEntity(String name) {
        this.name = name;
    }
}