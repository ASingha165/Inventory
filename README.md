# Inventory Studio

A full-stack stock management project built with Spring Boot 4, Java 17+, Spring Security, JPA and a responsive browser dashboard.

## Run in VS Code

1. Install a JDK (Java 17 or newer) and VS Code's Extension Pack for Java.
2. Open this folder in VS Code.
3. Run `./mvnw spring-boot:run` in the integrated terminal (Windows: `.\mvnw.cmd spring-boot:run`). If the Unix executable flag is unavailable, use `bash mvnw spring-boot:run`.
4. Open http://localhost:8080. Create an account; the browser signs you in after registration.
5. Add a product, receive stock with a positive adjustment, and remove units with a negative adjustment. Every quantity change is recorded.

No separate frontend build, account seed, paid service or MySQL setup is required. File-backed H2 persists accounts and inventory in `data/`. This directory is ignored by git. Sessions expire after 30 minutes of inactivity and are in memory; restarting the server signs users out without deleting their account data.

## Features

- Signup/login/logout with BCrypt password hashing and server-managed HttpOnly sessions.
- CSRF protection for authentication and all other writes. Login rotates the session ID and CSRF token.
- Private products and movement history for each account.
- Product CRUD: SKU, category, quantity, reorder threshold and exact decimal unit price in INR.
- Search, low-stock/out-of-stock filters, inventory totals and CSV export.
- Stock adjustments with a required reason, non-negative balances and optimistic locking.
- Delete products only after stock reaches zero; retain historical movements.
- Consistent JSON validation/authentication/conflict errors.

## Architecture and authentication

Browser -> Spring Security session/CSRF filters -> controllers -> transactional inventory service -> JPA -> H2/MySQL.

`auth/SecurityConfig.java` defines public assets and login/signup/CSRF routes; every other route requires authentication. `AuthController` stores BCrypt hashes, authenticates through Spring Security, and saves the SecurityContext into the server session. It never returns password hashes. Products are always loaded by both ID and the authenticated owner's email. The client never sets the owner.

Passwords need 10 characters and must fit within BCrypt's 72-byte UTF-8 limit. Authentication endpoints have a per-process limit of 20 requests per remote IP per 15 minutes. For deployments behind a proxy, add edge rate limiting; the application does not trust arbitrary forwarded IP headers.

The browser obtains `/api/auth/csrf` and sends the returned header/token on writes. It fetches a new token after login/logout. No auth tokens or passwords are persisted in browser storage. Sessions use HttpOnly and SameSite=Strict cookies. Set `COOKIE_SECURE=true` behind HTTPS; the local HTTP default is false.

## API

| Method | Route | Purpose |
|---|---|---|
| GET | `/api/auth/csrf` | CSRF header name and token |
| POST | `/api/auth/signup` | `{name,email,password}` |
| POST | `/api/auth/login` | `{email,password}` |
| GET | `/api/auth/me` | Safe current account |
| POST | `/api/auth/logout` | Invalidate session |
| GET / POST | `/api/products` | List/create private products |
| GET / PUT | `/api/products/{id}` | Read/update product metadata |
| POST | `/api/products/{id}/adjustments` | `{delta,reason,version}` |
| DELETE | `/api/products/{id}?version=…` | Delete zero-stock product |
| GET | `/api/movements` | Latest 100 private movements |

Create product body: `{"sku":"BOOK-001","name":"Notebook","category":"Office","quantity":20,"reorderLevel":5,"price":"24.50"}`.

Updates include the returned `version` and unchanged `quantity`. Change quantity through adjustments. Stale versions return 409, including repeated adjustments. Values are limited to 0–1,000,000 units; prices allow two decimal places. SKU uniqueness is per account and case-insensitive. The previous unauthenticated root CRUD endpoints have been replaced by `/api/products`.

## Configuration

| Variable | Default / purpose |
|---|---|
| `PORT` | `8080` |
| `DATABASE_URL` | `jdbc:h2:file:./data/inventory;DB_CLOSE_ON_EXIT=FALSE` |
| `DATABASE_USERNAME` | `sa` for local H2 |
| `DATABASE_PASSWORD` | Empty for local H2; set for hosted databases |
| `COOKIE_SECURE` | `false` for local HTTP; `true` behind HTTPS |

For MySQL, create a dedicated database/user and set a JDBC URL such as `jdbc:mysql://localhost:3306/inventory` plus username/password **in your terminal environment**. Environment files are not automatically loaded by Spring; export variables or configure your run environment in VS Code. Never put credentials into tracked configuration. Hibernate `update` is used for this demo; use reviewed migrations before operating a production database.

**Existing users:** the committed database password was removed, but its past exposure remains. Rotate that password with your database provider. The old unowned inventory schema is not automatically migrated into private accounts; back up any existing MySQL data and use a fresh database for this version. Java 17 is now the minimum; newer JDKs remain compatible.

## Verification

`./mvnw test` runs isolated H2 integration tests covering authentication, CSRF, cross-account access, validation, stale changes, stock conservation and historical retention. `./mvnw package` builds an executable JAR. CI runs the same tests.

## Container

`docker build -t inventory-studio .`

`docker run --rm -p 8080:8080 -v inventory-data:/app/data inventory-studio`

Use a persistent volume, HTTPS reverse proxy, `COOKIE_SECURE=true`, database backups and external rate limiting for hosting. The local H2 profile is intended for a single application instance. No email verification/password reset or shared multi-instance sessions are implemented.
