package com.inventory.inventorys.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class StockMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public String ownerEmail;

  public Long productId;
  public String productName;
  public int delta;
  public int resultingQuantity;

  @Column(length = 200)
  public String reason;

  public Instant createdAt = Instant.now();
}
