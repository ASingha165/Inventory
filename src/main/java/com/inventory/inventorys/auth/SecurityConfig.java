package com.inventory.inventorys.auth;

import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService users(AccountRepository accounts) {
    return email ->
        accounts
            .findByEmail(email)
            .map(a -> User.withUsername(a.email).password(a.passwordHash).roles("USER").build())
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  @Bean
  AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
    var provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  @Bean
  HttpSessionSecurityContextRepository contextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, HttpSessionSecurityContextRepository contexts)
      throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/index.html",
                        "/app.css",
                        "/app.js",
                        "/api/auth/csrf",
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .securityContext(c -> c.securityContextRepository(contexts))
        .requestCache(c -> c.disable())
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'; connect-src"
                                + " 'self'; img-src 'self' data:; frame-ancestors 'none';"
                                + " form-action 'self'; base-uri 'none'")))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          res.getWriter().write("{\"error\":\"Please sign in.\"}");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          res.getWriter()
                              .write(
                                  "{\"error\":\"Request rejected. Refresh the page and try"
                                      + " again.\"}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler(
                        (req, res, auth) -> {
                          res.setStatus(200);
                          res.setContentType("application/json");
                          res.getWriter().write("{\"success\":true}");
                        })
                    .deleteCookies("JSESSIONID"))
        .build();
  }
}
