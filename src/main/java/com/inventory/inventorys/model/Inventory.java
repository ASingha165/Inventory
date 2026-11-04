package com.inventory.inventorys.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(
    name = "inventory",
    uniqueConstraints = @UniqueConstraint(columnNames = {"owner_email", "sku"}))
public class Inventory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "owner_email", nullable = false)
  public String ownerEmail;

  @Column(nullable = false, length = 60)
  public String sku;

  @Column(nullable = false, length = 120)
  public String name;

  @Column(nullable = false, length = 60)
  public String category;

  public int quantity;
  public int reorderLevel;

  @Column(nullable = false, precision = 14, scale = 2)
  public BigDecimal price;

  @Version public Long version;
}
