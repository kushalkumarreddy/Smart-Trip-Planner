# VoyageCraft AI Travel Planner — Application Security Review & Hardening Report

**Date**: October 2026  
**Auditor**: Application Security & Infrastructure Engineering Team  
**Scope**: Full application stack (Spring Security, REST Controllers, Service Layer, Database Access, CSRF Protection, and Container Environment)  
**Status**: Hardened & Verified

---

## 1. Executive Summary

This Security Review documents the vulnerability assessment, threat modeling, and defensive hardening performed on the **VoyageCraft AI Smart Travel Planner & Route Optimizer**.

Prior to this review, the codebase exhibited critical vulnerabilities:
1. **Unauthenticated Access**: All `/api/**` endpoints were permitted without authentication.
2. **Insecure Demo-User Fallback**: Unauthenticated requests automatically mutated state on behalf of a hardcoded demo user.
3. **Missing Authorization in Toggle Operations**: Visited status toggles bypassed collaborator and ownership checks completely.
4. **CSRF Vulnerability**: CSRF verification was globally disabled for all API requests.
5. **Information Leakage**: Unhandled exceptions exposed database table and syntax details to clients.

Through this remediation, all identified vulnerabilities have been eliminated. Multi-layered defense-in-depth has been established across Spring Security filters, controller guards, service-level ownership checks, CSRF token validation, and error sanitization.

---

## 2. Threat Modeling & Attack Surface Analysis

```
       [ Client Browser / Mobile / API Client ]
                         |
                 (TLS 1.3 / HTTPS)
                         v
           [ Spring Security Filter Chain ]
       - CookieCsrfTokenRepository (CSRF Check)
       - AuthenticationFilter (Session / BCrypt)
       - SecurityFilterChain Authorization Rules
                         |
                         v
               [ Controller Layer ]
       - @AuthenticationPrincipal UserDetails validation
       - Jakarta Bean Validation (@Valid, @NotNull, @Size)
                         |
                         v
                [ Service Layer ]
       - checkTripAccess(trip, user, requiredRole)
       - Strict non-null User enforcement
       - Multi-tenant isolation (Owner / Collaborator check)
                         |
                         v
       [ Database Access (JPA / Hibernate) ]
       - Parameterized prepared queries (SQLi immune)
       - Transaction boundaries (@Transactional)
```

---

## 3. Vulnerability Findings & Resolutions

### SEC-01: Permissive Route Configuration (Resolved)
- **Vulnerability**: `/api/**`, `/create-trip`, and `/trip/**` were set to `.permitAll()` in `SecurityConfig.java`.
- **Risk**: Unauthenticated anonymous actors could view private itineraries, delete destinations, and forge travel crews.
- **Remediation**:
  - Configured `SecurityFilterChain` to require authentication (`.authenticated()`) for:
    - `/dashboard`
    - `/create-trip`
    - `/trip/**`
    - State-mutating API routes (`/api/trips/**`)
  - Permitted only public discovery routes: `/`, `/login`, `/register`, `/css/**`, `/js/**`, `/images/**`, `/api/places/autocomplete`, `/api/destinations/**`.

### SEC-02: Insecure Demo User Fallback (Resolved)
- **Vulnerability**: `resolveCurrentUser()` in `TripApiController` and `WebController` automatically created or resolved a demo user (`traveler@example.com`) if the caller was unauthenticated.
- **Risk**: Anonymous requests hijacked or deleted the demo user's itineraries without logging in.
- **Remediation**:
  - Removed demo-user fallback for all modifying operations.
  - Controllers now strictly enforce authenticated user presence and throw `AccessDeniedException` or redirect unauthenticated visitors to `/login`.

### SEC-03: Authorization Bypass in Visited Toggles (Resolved)
- **Vulnerability**: `togglePlaceVisited()` and `toggleDestinationVisited()` accepted `null` user and contained `if (user != null) checkTripAccess(...)`, silently bypassing authorization when user was null.
- **Risk**: Any visitor knowing a place or destination ID could toggle visited markers for any trip.
- **Remediation**:
  - Made user parameter mandatory.
  - Enforced `checkTripAccess(trip, user, "EDITOR")` unconditionally before modifying state.
  - Added unit tests in `PersistenceAndSecurityTest.java` verifying that null user throws `AccessDeniedException`.

### SEC-04: Cross-Site Request Forgery (CSRF) Hardening (Resolved)
- **Vulnerability**: CSRF protection was completely ignored for `/api/**`.
- **Risk**: Malicious third-party websites could trigger state mutations (e.g., deleting trips or reordering stops) using an active user session.
- **Remediation**:
  - Configured `CookieCsrfTokenRepository.withHttpOnlyFalse()` with `CsrfTokenRequestAttributeHandler` in `SecurityConfig.java`.
  - Injected CSRF meta tags (`<meta name="_csrf">` and `<meta name="_csrf_header">`) into all HTML templates (`dashboard.html`, `trip-details.html`, `create-trip.html`, `login.html`, `register.html`).
  - Added global CSRF fetch interceptor in `app.js` that automatically attaches `X-XSRF-TOKEN` / `X-CSRF-TOKEN` to all `POST`, `PUT`, `DELETE`, and `PATCH` requests.

### SEC-05: Stack Trace & Database Detail Leakage (Resolved)
- **Vulnerability**: `GlobalExceptionHandler.java` returned `ex.getMessage()` on generic exceptions, leaking database connection URLs, constraint names, and SQL syntax.
- **Risk**: Reconnaissance by attackers to identify backend schema and database technology.
- **Remediation**:
  - Sanitized generic 500 error responses:
    `"An unexpected error occurred while processing your travel itinerary. Please try again later."`
  - Stack traces are logged securely on the server via SLF4J logger at `ERROR` level without reaching client JSON responses.

### SEC-06: Secrets Management & Environment Hygiene (Resolved)
- **Vulnerability**: No `.gitignore` or `.dockerignore` existed; production database credentials risked being checked into source control.
- **Risk**: Hardcoded credential exposure and Git history leakage.
- **Remediation**:
  - Created `.gitignore` excluding `.env*`, `target/`, `*.jar`, IDE configs, and logs.
  - Created `.dockerignore` preventing secrets, local databases, and temporary build files from entering container images.
  - Created `.env.example` with sanitized placeholders.

### SEC-07: Guest Trip Planning & Multi-Tenant Boundary Isolation (Resolved)
- **Design Requirement**: Unauthenticated visitors must be allowed to plan itineraries, calculate routes, search smart stops, and customize schedules without being forced to login.
- **Risk**: Opening endpoints could accidentally allow unauthenticated guests to read or overwrite persisted user trips by tampering with database IDs.
- **Remediation**:
  - Guest planning operates purely on transient, in-memory/session-scoped DTOs (`id: 0`).
  - Guest endpoints (`/api/trips/guest-plan`, `/api/trips/guest/**`, `/trip/guest`) are explicitly separated from database entity endpoints (`/api/trips/{id}/**`).
  - Guests cannot mutate or access any database records.
  - Converting a guest trip into a permanent trip (`POST /api/trips/save-guest`) strictly enforces `@AuthenticationPrincipal UserDetails`.

---

## 4. Authorization & Permission Matrix

| Operation | Anonymous / Guest | Authenticated (Non-Crew) | Collaborator (VIEWER) | Collaborator (EDITOR) | Owner |
|---|---|---|---|---|---|
| View Public Catalog / Autocomplete | ALLOW | ALLOW | ALLOW | ALLOW | ALLOW |
| Plan & Recalculate Guest Trip (`/create-trip`, `/api/trips/guest/**`) | ALLOW (Session Only) | ALLOW | ALLOW | ALLOW | ALLOW |
| Save Guest Trip to Account (`POST /api/trips/save-guest`) | 401 Prompt Login | ALLOW (Saved to Self) | N/A | N/A | ALLOW |
| View Dashboard (`/dashboard`) | REDIRECT `/login` | ALLOW (Own Trips) | ALLOW (Own Trips) | ALLOW (Own Trips) | ALLOW |
| Create Trip (`POST /api/trips`) | REDIRECT `/login` | ALLOW | ALLOW | ALLOW | ALLOW |
| View Trip Details (`GET /trip/{id}`) | REDIRECT `/login` | 403 Forbidden | ALLOW | ALLOW | ALLOW |
| Reorder Destinations (`POST /api/trips/{id}/reorder`) | 403 Forbidden | 403 Forbidden | 403 Forbidden | ALLOW | ALLOW |
| Toggle Place / Destination Visited | 403 Forbidden | 403 Forbidden | 403 Forbidden | ALLOW | ALLOW |
| Edit Stay Duration & Accommodation | 403 Forbidden | 403 Forbidden | 403 Forbidden | ALLOW | ALLOW |
| Restore Version (`POST /api/trips/{id}/versions/{v}/restore`) | 403 Forbidden | 403 Forbidden | 403 Forbidden | ALLOW | ALLOW |
| Delete Trip (`DELETE /api/trips/{id}`) | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | ALLOW |

---

## 5. Security Verification Tests

Automated security regression tests implemented in `PersistenceAndSecurityTest.java`:
1. `testUnauthenticatedUserAccessDenied`: Verifies null user throws `AccessDeniedException` with message "Authentication required".
2. `testIntruderAccessDenied`: Verifies non-owner non-collaborator user is rejected with `AccessDeniedException`.
3. `testCollaboratorGrantedAccess`: Verifies invited crew collaborator with `EDITOR` role is permitted.
4. `testToggleVisitedUnauthenticatedAccessDenied`: Verifies place and destination visited toggles strictly reject unauthenticated users.

---

## 6. Security Checklist for Production Deployment

- [x] Spring Security enabled on all sensitive routes
- [x] CSRF protection active with automatic token transmission
- [x] Passwords encrypted using BCrypt (`PasswordEncoder`)
- [x] OWASP Top 10 SQL Injection prevented via Hibernate parameterization
- [x] XSS prevented via Thymeleaf HTML escaping and contextual JS encoding
- [x] Dockerfile runs as unprivileged user (`appuser:appgroup` UID 10001)
- [x] No hardcoded production secrets in repository
- [x] Error messages sanitized in production mode
- [x] Database connections configured with TLS support (`useSSL=true`)
- [x] Production Hibernate strategy set to `ddl-auto=validate`
