package com.inventory.inventorys.Controller;

import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("error", e.getReason() == null ? "Request rejected." : e.getReason()));
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<?> auth() {
    return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password."));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    var field = e.getBindingResult().getFieldErrors().get(0);
    return ResponseEntity.badRequest()
        .body(Map.of("error", field.getField() + ": " + field.getDefaultMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<?> json() {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "Send valid JSON with the expected field types."));
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(Map.of("error", "Duplicate or changed record. Refresh and try again."));
  }
}
