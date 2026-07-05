# Insurance System for Health (ISH)

A health insurance management system covering the full application lifecycle — registration, data
collection, eligibility, correspondence, benefit issuance, and government reporting.

## 📋 Overview

ISH was originally built as 12 Spring Boot microservices. It has since been **consolidated into a
single Kotlin + Spring Boot 3.3 modular monolith** (`healthcare/`) that runs as one deployable app on
port **8080**. Each former service is now a module package under `com.lakshay.healthcare.<module>`;
calls that used to be HTTP (Eureka/Gateway/WebClient) are now in-process `@Service` bean calls.

> The microservice layout (Eureka, Config Server, API Gateway, per-service ports,
> `start-all-services.bat`) no longer applies — those directories were removed on this branch.

## 🏗️ Modules

All under `com.lakshay.healthcare`:

| Module | Responsibility |
|--------|----------------|
| `user` | User/worker registration, activation, authentication |
| `application` | Citizen applications for health insurance |
| `data` | Applicant data — income, education, dependents |
| `eligibility` | Eligibility determination from collected data |
| `benefit` | Benefit issuance (Spring Batch CSV job) |
| `correspondence` | Applicant notifications (PDF + email) |
| `report` | Government agency reports |
| `admin` | Plan/category management and admin functions |
| `ssa` | In-process SSN validation (formerly the SSA web API) |

Shared JPA entities, repositories, security, and config live under `com.lakshay.healthcare.shared`.

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Maven (the bundled `mvnw` wrapper works without a local install)
- MySQL 8.0 or higher
- Docker (only for the integration tests — they spin up MySQL via Testcontainers)

### Database Setup

1. Create a MySQL database named `InsuranceSystemForHealth` at `localhost:3306` (credentials
   `root`/`root`, configured in `healthcare/src/main/resources/application.yml`).
2. The schema is owned by **Flyway** — migrations under `src/main/resources/db/migration/` run on
   startup. Hibernate is `ddl-auto: validate`, so the DB must exist before the app boots.

### Environment Variables

```bash
# Windows
set MAIL_USERNAME=your_gmail_address
set MAIL_PASSWORD=your_app_password_here

# Linux/Mac
export MAIL_USERNAME=your_gmail_address
export MAIL_PASSWORD=your_app_password_here
```

`jwt.secret` is also read from the environment (with an insecure dev fallback). For Gmail, generate an
App Password: Google Account → Security → 2-Step Verification → App passwords → "Mail".

### Build & Run

Run from the `healthcare/` directory:

```powershell
.\mvnw clean install -DskipTests   # build, skip tests
.\mvnw spring-boot:run             # run the app (port 8080)
.\mvnw test                        # run the full test suite
```

Swagger UI: http://localhost:8080/swagger-ui.html

## 📚 API

All endpoints are served by the single app on **port 8080**. REST paths are preserved from the
original microservices.

### User / Worker management (`user`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/user-api/save` | POST | Register a new user |
| `/user-api/activate` | POST | Activate a user account |
| `/user-api/login` | POST | User login |
| `/user-api/report`, `/find/{id}`, `/update`, `/delete/{id}`, `/changeStatus/{id}/{status}` | GET/PUT/DELETE/PATCH | User administration (ADMIN only) |
| `/worker-api/...` | — | Same set for workers (admin routes ADMIN only) |
| `/api/auth/login` | POST | Combined user/worker/admin login |

### Citizen application (`application`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/CitizenAR-api/save` | POST | Register a new citizen application |

### Data collection (`data`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/dc-api/loadCaseNo/{appId}` | POST | Generate a case number for an application |
| `/dc-api/planNames` | GET | Get all plan names |
| `/dc-api/updatePlanSelection` | PUT | Update plan selection |
| `/dc-api/saveIncome` | POST | Save income details |
| `/dc-api/saveEducation` | POST | Save education details |
| `/dc-api/saveChilds` | POST | Save children details |
| `/dc-api/summary/{caseNo}` | GET | Assembled case summary |

### Eligibility (`eligibility`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/ed-api/determine/{caseNo}` | GET | Determine eligibility for a case |

### Admin / plans (`admin`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/plan-api/categories` | GET | Get all plan categories (public) |
| `/plan-api/register`, `/all`, `/find/{id}`, `/update`, `/delete/{id}`, `/status-change/{id}/{status}` | various | Plan management (ADMIN) |
| `/admin-api/create` | POST | Create an admin (ADMIN) |
| `/admin/worker-api/...` | various | Worker management by admin (ADMIN) |

### Correspondence (`correspondence`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/co-triggers-api/process` | GET | Process pending correspondence triggers |

### Benefit issuance (`benefit`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/bi-api/send` | GET | Launch the benefit-issuance batch job |

### Government reports (`report`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/report-api/government/*` | various | Generate / list / fetch / download / delete reports (ADMIN or WORKER) |

A Postman collection lives under `ISH-Postman-collections/`.

## 🔧 Technical Details

- **Language**: Kotlin 1.9
- **Framework**: Spring Boot 3.3
- **Database**: MySQL 8.0, schema managed by Flyway
- **Batch**: Spring Batch (benefit issuance → CSV)
- **Documentation**: springdoc OpenAPI / Swagger UI
- **Authentication**: stateless JWT (`io.jsonwebtoken`)
- **PDF / Excel**: OpenPDF, Apache POI

## 🔒 Security

- JWT-based authentication (subject + issuer + audience + expiry validated)
- BCrypt password hashing
- Role-based access control (`ADMIN`, `WORKER`); rules in `shared/config/SecurityConfig.kt`

## 🧪 Testing

Integration tests run against a real MySQL 8 container via **Testcontainers** (Docker required),
exercising the full filter chain and Flyway migrations:

```powershell
.\mvnw test
```
