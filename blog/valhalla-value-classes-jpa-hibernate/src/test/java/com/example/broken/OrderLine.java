package com.example.broken;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

@Entity
@IdClass(LineKey.class)
public class OrderLine {
    @Id
    private Long orderNo;

    @Id
    private Integer lineNo;

    private String note;

    protected OrderLine() {}

    public OrderLine(Long orderNo, Integer lineNo, String note) {
        this.orderNo = orderNo;
        this.lineNo = lineNo;
        this.note = note;
    }

    public String getNote() {
        return note;
    }
}
