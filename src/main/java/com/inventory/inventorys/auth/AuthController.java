package com.inventory.inventorys.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AccountRepository accounts;
  private final PasswordEncoder encoder;
  private final AuthenticationManager manager;
  private final HttpSessionSecurityContextRepository contexts;
  private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

  private record Attempt(int count, long until) {}

  public record Signup(
      @NotBlank @Size(min = 2, max = 80) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 10, max = 72) String password) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  public AuthController(
      AccountRepository a,
      PasswordEncoder e,
      AuthenticationManager m,
      HttpSessionSecurityContextRepository c) {
    accounts = a;
    encoder = e;
    manager = m;
    contexts = c;
  }

  private void throttle(HttpServletRequest req) {
    long now = System.currentTimeMillis();
    attempts.entrySet().removeIf(e -> e.getValue().until() < now);
    var result =
        attempts.compute(
            req.getRemoteAddr(),
            (key, old) ->
                new Attempt(
                    old == null ? 1 : old.count() + 1, old == null ? now + 900000 : old.until()));
    if (result.count() > 20)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try later.");
  }

  private String email(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private Map<String, Object> safe(Account a) {
    return Map.of("id", a.id, "name", a.name, "email", a.email);
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> signup(@Valid @RequestBody Signup body, HttpServletRequest req) {
    throttle(req);
    if (body.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password must fit within 72 UTF-8 bytes.");
    if (accounts.findByEmail(email(body.email())).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered.");
    var a = new Account();
    a.name = body.name().trim();
    a.email = email(body.email());
    a.passwordHash = encoder.encode(body.password());
    accounts.save(a);
    return safe(a);
  }

  @PostMapping("/login")
  public Map<String, Object> login(
      @Valid @RequestBody Login body, HttpServletRequest req, HttpServletResponse res) {
    throttle(req);
    if (body.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    var authentication =
        manager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                email(body.email()), body.password()));
    if (req.getSession(false) != null) req.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, req, res);
    new HttpSessionCsrfTokenRepository().saveToken(null, req, res);
    return safe(accounts.findByEmail(authentication.getName()).orElseThrow());
  }

  @GetMapping("/me")
  public Map<String, Object> me(Principal principal) {
    return safe(accounts.findByEmail(principal.getName()).orElseThrow());
  }
}
