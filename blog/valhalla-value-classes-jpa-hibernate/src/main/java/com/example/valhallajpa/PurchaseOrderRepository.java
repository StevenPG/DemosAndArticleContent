package com.example.valhallajpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, OrderId> {
    List<PurchaseOrder> findBySku(Sku sku);

    List<PurchaseOrder> findByTotal(Money total);
}
