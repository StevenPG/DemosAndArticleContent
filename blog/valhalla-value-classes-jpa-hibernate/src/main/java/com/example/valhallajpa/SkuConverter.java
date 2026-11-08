package com.example.valhallajpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SkuConverter implements AttributeConverter<Sku, String> {
    @Override
    public String convertToDatabaseColumn(Sku sku) {
        return sku == null ? null : sku.code();
    }

    @Override
    public Sku convertToEntityAttribute(String code) {
        return code == null ? null : new Sku(code);
    }
}
