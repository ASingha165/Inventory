package com.inventory.inventorys.repository;

import com.inventory.inventorys.model.StockMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovementRepository extends JpaRepository<StockMovement, Long> {
  List<StockMovement> findTop100ByOwnerEmailOrderByCreatedAtDescIdDesc(String email);
}
