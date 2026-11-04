package com.inventory.inventorys.service;

import com.inventory.inventorys.model.*;
import com.inventory.inventorys.repository.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class InventoryService {
  private final InventoryRepository products;
  private final MovementRepository movements;

  public InventoryService(InventoryRepository p, MovementRepository m) {
    products = p;
    movements = m;
  }

  public record ProductInput(
      @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9_-]+") String sku,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 60) String category,
      @Min(0) @Max(1000000) int quantity,
      @Min(0) @Max(1000000) int reorderLevel,
      @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
          BigDecimal price,
      Long version) {}

  public record Adjustment(
      @NotNull @Min(-1000000) @Max(1000000) Integer delta,
      @NotBlank @Size(max = 200) String reason,
      @NotNull Long version) {}

  public record ProductView(
      Long id,
      String sku,
      String name,
      String category,
      int quantity,
      int reorderLevel,
      BigDecimal price,
      Long version) {}

  public ProductView view(Inventory p) {
    return new ProductView(
        p.id, p.sku, p.name, p.category, p.quantity, p.reorderLevel, p.price, p.version);
  }

  public Inventory owned(Long id, String email) {
    return products
        .findByIdAndOwnerEmail(id, email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
  }

  public List<ProductView> list(String email) {
    return products.findByOwnerEmailOrderByNameAsc(email).stream().map(this::view).toList();
  }

  private void record(Inventory p, int delta, String reason) {
    var m = new StockMovement();
    m.ownerEmail = p.ownerEmail;
    m.productId = p.id;
    m.productName = p.name;
    m.delta = delta;
    m.resultingQuantity = p.quantity;
    m.reason = reason;
    movements.save(m);
  }

  public ProductView create(String email, ProductInput input) {
    String sku = input.sku().toUpperCase(Locale.ROOT);
    if (products.existsByOwnerEmailAndSku(email, sku))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists.");
    var p = new Inventory();
    p.ownerEmail = email;
    p.sku = sku;
    assign(p, input);
    p.quantity = input.quantity();
    products.saveAndFlush(p);
    record(p, p.quantity, "Opening stock");
    return view(p);
  }

  private void assign(Inventory p, ProductInput input) {
    p.name = input.name().trim();
    p.category = input.category().trim();
    p.reorderLevel = input.reorderLevel();
    p.price = input.price();
  }

  public ProductView update(Long id, String email, ProductInput input) {
    var p = owned(id, email);
    checkVersion(p, input.version());
    if (input.quantity() != p.quantity)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Use a stock adjustment to change quantity.");
    var sku = input.sku().toUpperCase(Locale.ROOT);
    if (!p.sku.equals(sku) && products.existsByOwnerEmailAndSku(email, sku))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists.");
    p.sku = sku;
    assign(p, input);
    products.saveAndFlush(p);
    return view(p);
  }

  public ProductView adjust(Long id, String email, Adjustment input) {
    var p = owned(id, email);
    checkVersion(p, input.version());
    if (input.delta() == 0
        || p.quantity + (long) input.delta() < 0
        || p.quantity + (long) input.delta() > 1000000)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Adjustment must change stock and leave 0–1,000,000 units.");
    p.quantity += input.delta();
    products.saveAndFlush(p);
    record(p, input.delta(), input.reason().trim());
    return view(p);
  }

  public void delete(Long id, String email, Long version) {
    var p = owned(id, email);
    checkVersion(p, version);
    if (p.quantity != 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Reduce stock to zero before deleting a product.");
    record(p, 0, "Product deleted");
    products.delete(p);
  }

  private void checkVersion(Inventory p, Long version) {
    if (version == null || !version.equals(p.version))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Product changed. Refresh and try again.");
  }

  public List<StockMovement> history(String email) {
    return movements.findTop100ByOwnerEmailOrderByCreatedAtDescIdDesc(email);
  }
}
