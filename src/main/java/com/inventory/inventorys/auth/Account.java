package com.inventory.inventorys.auth;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 254)
  public String email;

  @Column(nullable = false, length = 80)
  public String name;

  @Column(nullable = false)
  public String passwordHash;
}
