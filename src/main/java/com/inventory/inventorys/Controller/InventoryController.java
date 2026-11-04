package com.inventory.inventorys.Controller;

import com.inventory.inventorys.service.InventoryService;
import com.inventory.inventorys.service.InventoryService.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class InventoryController {
  private final InventoryService service;

  public InventoryController(InventoryService service) {
    this.service = service;
  }

  @GetMapping("/products")
  public List<ProductView> list(Principal user) {
    return service.list(user.getName());
  }

  @GetMapping("/products/{id}")
  public ProductView get(@PathVariable Long id, Principal user) {
    return service.view(service.owned(id, user.getName()));
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductView create(@Valid @RequestBody ProductInput input, Principal user) {
    return service.create(user.getName(), input);
  }

  @PutMapping("/products/{id}")
  public ProductView update(
      @PathVariable Long id, @Valid @RequestBody ProductInput input, Principal user) {
    return service.update(id, user.getName(), input);
  }

  @PostMapping("/products/{id}/adjustments")
  public ProductView adjust(
      @PathVariable Long id, @Valid @RequestBody Adjustment input, Principal user) {
    return service.adjust(id, user.getName(), input);
  }

  @DeleteMapping("/products/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id, @RequestParam Long version, Principal user) {
    service.delete(id, user.getName(), version);
  }

  @GetMapping("/movements")
  public Object movements(Principal user) {
    return service.history(user.getName());
  }
}
