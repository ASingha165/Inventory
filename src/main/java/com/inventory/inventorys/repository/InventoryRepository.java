package com.inventory.inventorys.repository;

import com.inventory.inventorys.model.Inventory;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
  List<Inventory> findByOwnerEmailOrderByNameAsc(String ownerEmail);

  Optional<Inventory> findByIdAndOwnerEmail(Long id, String ownerEmail);

  boolean existsByOwnerEmailAndSku(String ownerEmail, String sku);
}
