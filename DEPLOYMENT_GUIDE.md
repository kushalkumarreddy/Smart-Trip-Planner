# VoyageCraft AI Travel Planner — Production Deployment Guide

**Version**: 1.0.0  
**Stack**: Java 21 / Spring Boot 3.3.4 / MySQL 8.0+ / Docker / Maven  

---

## 1. Prerequisites

Before deploying VoyageCraft, ensure the following tools and services are installed:
- **Java Development Kit (JDK)**: Version 21 (Eclipse Temurin or OpenJDK 21)
- **Maven**: Version 3.9+ (or use the included Maven wrapper `./mvnw` / `mvnw.cmd`)
- **Database**: MySQL 8.0 or newer
- **Docker & Docker Compose** (Optional, for containerized deployment): Docker Engine 24+ and Compose v2+
- **Google Maps API Key** (Optional): For live Google Places and Directions APIs
- **AI API Key** (Optional): For generative natural language trip itinerary planning

---

## 2. Environment Variables Configuration

VoyageCraft uses environment variables to configure database connections, external APIs, and runtime profiles.

1. Copy the provided `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Update the values in `.env`:

| Variable | Required | Default | Description |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `prod` | Active Spring profile (`prod` for production). |
| `PORT` | No | `8080` | Web server listening port. |
| `DB_HOST` | Yes (Option A) | `localhost` | MySQL host address. |
| `DB_PORT` | No | `3306` | MySQL port. |
| `DB_NAME` | Yes (Option A) | `trip_planner` | MySQL database name. |
| `DB_USERNAME` | Yes | `root` | Database username. |
| `DB_PASSWORD` | Yes | `root` | Database password. |
| `SPRING_DATASOURCE_URL` | Yes (Option B) | Auto-constructed | Full JDBC URL (overrides `DB_HOST`/`DB_NAME`). Useful for cloud databases like Clever Cloud or AWS RDS. |
| `DB_POOL_MAX` | No | `10` | Maximum HikariCP pool connections. |
| `DB_POOL_MIN_IDLE` | No | `4` | Minimum idle HikariCP connections. |
| `GOOGLE_MAPS_API_KEY` | No | `""` | Google Maps Platform API key (for live directions & places). |
| `GOOGLE_MAPS_ENABLED` | No | `true` | Enables Google Maps features when key is present. |
| `AI_API_KEY` | No | `""` | AI service API key for generative itineraries. |
| `SMARTSTOPS_OVERPASS_ENABLED` | No | `true` | Enables Overpass OpenStreetMap POI queries. |

---

## 3. Database Setup & Migrations

### A. Database Creation
Connect to your MySQL 8.0+ server and create the database with UTF-8 character encoding:
```sql
CREATE DATABASE IF NOT EXISTS trip_planner
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

### B. Applying Schema Migrations
VoyageCraft provides two migration options:
1. **Flyway Migration Script**: Execute `src/main/resources/db/migration/V1__init_schema.sql` against the database:
   ```bash
   mysql -h <DB_HOST> -u <DB_USERNAME> -p trip_planner < src/main/resources/db/migration/V1__init_schema.sql
   ```
2. **Standard SQL Schema**: Alternatively, apply `src/main/resources/schema.sql`:
   ```bash
   mysql -h <DB_HOST> -u <DB_USERNAME> -p < src/main/resources/schema.sql
   ```

### C. Persistent Storage & Backups
> [!IMPORTANT]
> Never store persistent database data on an ephemeral container filesystem. Always use a managed database service or a persistent Docker volume.

- **Automated Backup Command**:
  ```bash
  mysqldump -h <DB_HOST> -u <DB_USERNAME> -p --single-transaction --quick --lock-tables=false trip_planner > backup_$(date +%Y%m%d_%H%M%S).sql
  ```
- **Restore Command**:
  ```bash
  mysql -h <DB_HOST> -u <DB_USERNAME> -p trip_planner < backup_20261024.sql
  ```

---

## 4. Building & Running Locally in Production Mode

### Step 1: Package the Production Jar
Using the Maven wrapper:
- **On Linux / macOS**:
  ```bash
  ./mvnw clean package -DskipTests
  ```
- **On Windows**:
  ```cmd
  .\mvnw.cmd clean package -DskipTests
  ```
This generates `target/trip-planner-1.0.0.jar`.

### Step 2: Run with Production Profile
```bash
java -jar -Dspring.profiles.active=prod \
     -DDB_HOST=localhost \
     -DDB_NAME=trip_planner \
     -DDB_USERNAME=root \
     -DDB_PASSWORD=your_password \
     target/trip-planner-1.0.0.jar
```

The application will start on `http://localhost:8080`.

---

## 5. Docker Deployment

### Step 1: Build the Docker Image
```bash
docker build -t voyagecraft:1.0.0 .
```

### Step 2: Run the Docker Container
```bash
docker run -d \
  --name voyagecraft-app \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3306 \
  -e DB_NAME=trip_planner \
  -e DB_USERNAME=voyagecraft \
  -e DB_PASSWORD=your_secure_password \
  voyagecraft:1.0.0
```

---

## 6. Local Multi-Container Stack (Docker Compose)

To launch the complete application stack (Spring Boot + MySQL 8.0) locally with a single command:

```bash
docker compose up -d --build
```

- **Application**: Accessible at `http://localhost:8080`
- **MySQL Container**: Running on `localhost:3306` with persistent volume `mysql_data`
- **Logs**:
  ```bash
  docker compose logs -f app
  ```
- **Shutdown**:
  ```bash
  docker compose down
  ```

---

## 7. Cloud Hosting Recommendations

### Recommended Java Application Hosting
1. **Render (Web Service)**:
   - *Plan*: Free or Starter ($7/month).
   - *Build Command*: `./mvnw clean package -DskipTests`
   - *Start Command*: `java -jar target/trip-planner-1.0.0.jar`
   - *Notice*: Free instances sleep after 15 minutes of inactivity; warm-up takes 30-50 seconds.
2. **Railway (App Service)**:
   - *Plan*: Trial / Hobby ($5/month credit).
   - *Setup*: Deploy directly from Git repository using the included `Dockerfile`. Zero sleep on paid plans.
3. **Fly.io**:
   - *Plan*: Scale to 1 shared-cpu-1x (256MB/512MB RAM). Run via `fly launch` with the included `Dockerfile`.

### Recommended MySQL Database Hosting
1. **Aiven for MySQL**:
   - Free tier includes 1 service with 5GB storage and 1GB RAM. Fully managed MySQL 8.0 with automated backups and TLS support.
2. **Clever Cloud MySQL**:
   - Free tier provides a persistent MySQL add-on (approx 10MB free, scalable to paid).
3. **AWS RDS / Amazon Aurora**:
   - Production enterprise choice. Eligible for 12 months free tier (db.t3.micro, 20GB storage).

---

## 8. Health Checks & Verification

After deployment, verify that the application is operating correctly:
1. **HTTP Liveness Probe**:
   ```bash
   curl -I http://localhost:8080/
   ```
   *Expected Output*: `HTTP/1.1 200 OK`
2. **Authentication Check**:
   Navigate to `http://localhost:8080/dashboard` in a private/incognito browser window. It must cleanly redirect to `http://localhost:8080/login`.
3. **Pre-seeded Evaluation Account**:
   - Email: `traveler@example.com`
   - Password: `travel123`

---

## 9. Troubleshooting Common Failures

| Issue | Root Cause | Solution |
|---|---|---|
| `AccessDeniedException: Authentication required` | Calling private API without user session or auth cookie | Ensure user is logged in, or pass valid JSESSIONID cookie. |
| `HTTP 403 Forbidden` on POST/PUT/DELETE | Missing CSRF token header | Check that `app.js` CSRF interceptor is loaded or include `X-CSRF-TOKEN` / `X-XSRF-TOKEN` header. |
| `CommunicationsLinkException: Communications link failure` | MySQL service unreachable or incorrect credentials | Verify `DB_HOST`, `DB_PORT`, and network firewall rules. For Docker on Linux, use host IP instead of `host.docker.internal`. |
| `SchemaManagementException: Schema-validation: missing table` | Production Hibernate validation failed (`ddl-auto=validate`) | Apply `src/main/resources/db/migration/V1__init_schema.sql` to initialize all tables before starting. |
| `OutOfMemoryError: Java heap space` | Container memory constrained | Set `JAVA_OPTS="-XX:MaxRAMPercentage=75.0"` or assign at least 512MB RAM to the container. |
