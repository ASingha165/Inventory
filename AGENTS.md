# Project guide

Inventory Studio preserves the Spring Boot backend and serves a same-origin static UI.

- Java 17+, Spring Boot 4.0.5, Spring Security, JPA. Local H2; environment-configured MySQL is optional.
- `auth/` owns accounts and server sessions. `service/InventoryService` owns stock invariants and tenant isolation. Controllers expose `/api` only.
- Do not disable CSRF or allow wildcard credentialed CORS. Never persist passwords/tokens in browser storage.
- Never return Account entities; use safe responses without password hashes.
- Derive owner from the authenticated principal. Read/write by ID and owner, never ID alone.
- Use BigDecimal prices, nonnegative bounded quantities, @Version and transactional stock/movement writes.
- Existing databases require explicit reviewed migration; do not assign unowned legacy inventory to arbitrary accounts.
- DB files, build output and secrets stay out of git.
- Run `./mvnw test`; browser-smoke signup, product creation, stock adjustment and logout after relevant changes.
- 2026-11-04: added private account workspaces, CSRF-protected sessions, dashboard, validated CRUD, movement history and optimistic concurrency. Verification results are recorded in the PR.
