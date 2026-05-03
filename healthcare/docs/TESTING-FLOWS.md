# Functional Flow Test Spec

Source-of-truth backlog for the healthcare modular monolith's test suite. Every functional flow is
listed once, tiered **P0/P1/P2**, with enough detail that an author (human or agent) writes the test
without re-reading the service. Tests trace back to a flow ID here.

The repo currently has **zero real coverage** (only `contextLoads()`), so this doc doubles as the
work plan: burn down P0 → P1 → P2.

- Worked reference: [`GoldenLifecycleIT`](../src/test/kotlin/com/lakshay/healthcare/GoldenLifecycleIT.kt) implements **LIFE-1** end to end. Copy its shape.
- Harness: [`IntegrationTestBase`](../src/test/kotlin/com/lakshay/healthcare/support/IntegrationTestBase.kt) + [`application-test.yml`](../src/test/resources/application-test.yml).

---

## 1. Test layers

Two layers, chosen per flow (the `Layer` field in each entry):

| Layer | Style | Use for |
|-------|-------|---------|
| **IT** (integration) | `@SpringBootTest` + `MockMvc` through the real filter chain (JWT included) + Testcontainers MySQL | Anything crossing a module boundary, anything touching HTTP/security/serialization, the lifecycle, batch, email side effects. Class suffix `…IT`. |
| **Svc** (service-layer) | `@SpringBootTest`, inject the `@Service` bean, assert return + DB | Pure business rules with no HTTP concern — above all the **eligibility rule matrix**. Class suffix `…Test`. |

Why this monolith leans on IT: the defining trait is that former inter-service HTTP calls are now
**in-process bean calls** (`application→ssa`, `eligibility→data/application/admin`, `correspondence→eligibility`,
`benefit→eligibility`, `report→everything`). Mocking the neighbor defeats the consolidation, so the
cross-module seams are tested for real.

---

## 2. Harness (already built)

`IntegrationTestBase` provides, per test method:

- **Real MySQL 8** via Testcontainers `@ServiceConnection` (one container for the whole suite). Flyway
  runs the production migrations; `ddl-auto: validate` then proves entities match the schema — the
  same guarantee prod relies on. **Requires Docker** on the dev/CI machine (this repo has no CI yet —
  see §7).
- **`resetDb()` + `seedReference()`** in `@BeforeEach`: FK-disabled `TRUNCATE` of every domain table,
  then re-seed the data the app cannot run without — one **ADMIN** (`admin@ish.test` / `admin123`,
  inserted via repository because `/admin-api/create` itself needs ADMIN), one plan category, and the
  six keyword-named plans (`SNAP, CCAP, MEDCARE, MEDAID, CAJW, QHP`). State is **committed**, which the
  batch job and multi-request lifecycle require — a `@Transactional` rollback would not survive the
  job's own transactions.
- **`@MockBean JavaMailSender`** — nothing sends. `createMimeMessage()` is stubbed to a real
  `MimeMessage` so `EmailUtils` builds normally; assert with an `ArgumentCaptor<MimeMessage>` on
  `send(...)`.
- **`bearer(role, email)` / `adminAuth()`** — mint a token via the real `JwtUtil`. The filter only
  accepts a real `Bearer` header, so **`@WithMockUser` does not work here**; use these helpers. Token
  role strings are pre-prefixed: `"ROLE_ADMIN"`, `"ROLE_WORKER"`, `"ROLE_USER"`.
- **Batch metadata**: `application-test.yml` sets `spring.batch.jdbc.initialize-schema: always`
  (prod is `never`) so the fresh container gets `BATCH_*` tables. The CSV writes to
  `target/benefit_output_test.csv`; `BENEFIT_OUTPUT_FILE` is deleted before each test.

### Environment gotchas (already fixed in the build — recorded so they aren't re-debugged)

- **`--lower-case-table-names=1`** on the MySQL container (`IntegrationTestBase`): Linux MySQL is
  case-sensitive, but migrations create `UPPER_CASE` tables while Hibernate's Spring naming strategy
  looks them up lower-cased → `Schema-validation: missing table [admin_master]`. Windows/dev MySQL is
  case-insensitive, so this makes the container match prod.
- **`api.version=1.40`** pinned via the `maven-surefire-plugin` `systemPropertyVariables` in `pom.xml`:
  without it docker-java sends *unversioned* requests that Docker Desktop's named-pipe proxy rejects
  with HTTP 400 (`Could not find a valid Docker environment … Status 400`), so Testcontainers can't
  reach the engine even though the `docker` CLI works. Pinning any version ≥ the daemon `MinAPIVersion`
  makes docker-java send versioned requests.
- **Testcontainers `1.21.3`** pinned via `<testcontainers.version>` (Boot 3.3 BOM ships 1.19.x).
- **Kotlin nests block comments.** A KDoc that contains an endpoint glob like `` `/dc-api/*` `` opens a
  nested `/*` (the `i/*` sequence), and the KDoc's closing `*/` then balances the *inner* comment,
  leaving the outer one open → `Unclosed comment` at end of file. Write endpoint paths in KDoc without a
  trailing `/*` (e.g. "the `/dc-api` endpoints"), or the file won't compile.
- **Surefire `<includes>` for `*IT`** in `pom.xml`: Surefire excludes the `*IT` suffix by default (it's
  reserved for the Failsafe plugin's `integration-test` phase). Without the explicit include, `mvn test`
  *silently runs only* `HealthcareApplicationTests` and skips every IT class — green build, zero coverage.
  We list `**/*IT.java` in Surefire so `.\mvnw test` (the documented command) runs the whole suite.

---

## 3. Fixture rules

- **SSN** must be 9 digits and `ssn % 100 ∈ {1:WashingtonDC, 2:Ohio, 3:Texas, 4:California, 5:Florida}`
  (`SsnValidationService`). Any other 2-digit tail throws `InvalidSsnException`. Example valid: `123456704`.
- **Dates are relative to `now()`** — eligibility uses `LocalDate.now()` directly (no injectable `Clock`).
  Never hardcode a birth year. For age gates compute `LocalDate.now().minusYears(n)`:
  MEDCARE needs age ≥ 65, QHP ≥ 25, CCAP children ≤ 16, CAJW `passOutYear ≤ now().year`.
- **Eligibility rules switch on the plan *name* keyword** (uppercased). A plan whose name isn't one of
  the six keywords falls to the **default branch** (`totalIncome < 10000` → approve, benefit = 10%).
  Use the seeded keyword plans; register a non-keyword plan only when testing the default branch.
- **`planStatus` / `triggerStatus` strings are cross-module contracts** — `"APPROVED"` is what the
  benefit batch query and report counts read; `"PENDING"` is what correspondence consumes. Assert the
  exact strings.

---

## 4. Global negative / edge catalog

Reusable cases referenced by `Edge` fields below. Several encode **current behavior that is also a bug
risk** — assert the behavior as-is and flag (🚩) so a future fix is a deliberate, test-breaking change.

| Code | Condition | Expected today |
|------|-----------|----------------|
| E-AUTH-NONE | No `Authorization` header on a protected path | `401`, body "Missing or invalid Authorization header" (filter) |
| E-AUTH-BAD | Malformed/garbage token | `401` "Invalid token" |
| E-AUTH-EXP | Expired/wrong-audience token | `401` |
| E-ROLE | Authenticated but wrong role (e.g. USER → `/plan-api/all`) | `403` |
| E-SSN-LEN | SSN not 9 digits | `400`/handler → `InvalidSsnException` |
| E-SSN-STATE | SSN tail not in 1–5 | `InvalidSsnException` |
| E-DUP-EMAIL | Register user/worker/admin with existing email | `DuplicateResourceException` (409) |
| E-NOT-ACTIVE | Login before activation (`activeSw='N'`) | `401` "Account not activated…" |
| E-BAD-PW | Login wrong password | `401` "Invalid email or password" |
| E-CASE-DUP | `loadCaseNo` when a case already exists for appId | `DuplicateResourceException` (409) |
| E-NOTFOUND | Any `findBy…` miss (case/plan/report/user) | `ResourceNotFoundException` (404) |
| E-NO-PLAN | `determine` when case has no `planId` | `ResourceNotFoundException` "No plan selected" |
| E-NO-INCOME | `determine` when no income row | `ResourceNotFoundException` "Income data not found" |
| E-USER-ADMIN-ROLE | ROLE_USER/WORKER token hits `/user-api` or `/worker-api` management routes (report/find/update/delete/changeStatus) | `403` — now `hasRole("ADMIN")`. (Other `authenticated()`-only paths like `/dc-api/**`, `/co-triggers-api/**` remain reachable by any role by design.) |
| E-MAIL-FAIL | Mail send throws | Flow fails the trigger — `EmailUtils` returns `false`, `CorrespondenceService` throws, the outer loop flips the trigger to `FAILED` (batch continues for siblings). |
| E-CATEGORIES-PUBLIC | `GET /plan-api/categories` with no token | `200` — permitAll in `SecurityConfig` and in `JwtAuthFilter`'s open-path list, so the public endpoint is reachable token-less (asserted in `AuthIT`). |

> **MockMvc note:** `JwtAuthFilter` keys open-path detection off `request.servletPath`, which MockMvc leaves blank. Token-less requests to open endpoints must set it via the `servletPath(path)` helper on `IntegrationTestBase`, or they 401 spuriously. Token-carrying requests are unaffected (the filter validates the token regardless of path).

---

## 5. Flow entry template

Each entry carries: **ID · Title · Endpoint(s) · Layer · Auth/Role · Preconditions · DB-before ·
Steps · Expected · DB-after · Edge · Side effects · Couples-to · Tier**. Fields that don't apply are
omitted. "DB-before" assumes `seedReference()` ran; list only what the test adds on top.

---

## 6. Flow registry

### P0 — golden lifecycle, auth, role enforcement

#### LIFE-1 · Full insurance lifecycle ✅ *(implemented — `GoldenLifecycleIT`)*
- **Layer** IT · **Auth** ADMIN token throughout (covers every step; only `/plan-api` + `/report-api` are role-gated).
- **Steps**: `POST /CitizenAR-api/save` → `POST /dc-api/loadCaseNo/{appId}` → `PUT /dc-api/updatePlanSelection` (SNAP) → `POST /dc-api/saveIncome` (emp=100) → `GET /ed-api/determine/{caseNo}` → `GET /co-triggers-api/process` → `GET /bi-api/send` → `POST /report-api/government/generate`.
- **Expected**: SSN→`California`; eligibility `APPROVED` / benefit `200.0`; trigger `PENDING`→`PROCESSED`; CSV contains caseNo + `ISH-Bank`; report content `Total Applications: 1 / Approved: 1 / Denied: 0`.
- **DB-after**: 1 `CITIZEN_APPLICATION`, 1 `DC_CASES`+`DC_INCOME`, 1 `ELIGIBILITY_DETERMINATION (APPROVED)`, 1 `CO_TRIGGERS (PROCESSED, pdf set)`, 1 `ISH_GOVERNMENT_REPORTS`.
- **Side effects**: exactly one email to the citizen with a `benefit_notice_{caseNo}.pdf` attachment; CSV at `target/benefit_output_test.csv`.
- **Couples-to**: `application→ssa`; `eligibility` writes `ELIGIBILITY_DETERMINATION` + `CO_TRIGGERS(PENDING)`; `benefit` reads `planStatus='APPROVED'`; `correspondence` reads eligibility+case+citizen; `report` counts all.

#### AUTH-1 · User login
- **Endpoint** `POST /user-api/login` · **Layer** IT · **Auth** public.
- **Pre**: seed an activated user (`activeSw='Y'`, known password). **Steps**: post valid creds. **Expected** `200`, `LoginResponse.token` non-empty, `role="ROLE_USER"`, `userId` set. Validate the token via `JwtUtil` → subject=email, role claim `ROLE_USER`.
- **Edge**: E-BAD-PW, E-NOT-ACTIVE, E-NOTFOUND(email).

#### AUTH-2 · Worker login
- `POST /worker-api/login` · IT · public. Mirror AUTH-1 with `role="ROLE_WORKER"`, `workerId` set. Edge: E-BAD-PW, E-NOT-ACTIVE.

#### AUTH-3 · Combined login branching + role prefix
- `POST /api/auth/login` · IT · public. Seed one admin, one worker, one user (same password). Assert the endpoint resolves each by table lookup order **admin → worker → user**, returns `type` ∈ {ADMIN,WORKER,USER} and a token whose role claim is `ROLE_<type>`.
- **Critical assertion**: token role is **pre-prefixed** `ROLE_ADMIN` and `JwtAuthFilter` maps it verbatim to authority `ROLE_ADMIN`, which `hasRole("ADMIN")` matches. (Load-bearing and otherwise untested.)
- **Edge**: admin login path does **not** check `activeSw` (worker/user do — assert the difference). E-BAD-PW per branch.

#### AUTH-4 · JWT filter enforcement
- Any protected path · IT · token variants. Cases: E-AUTH-NONE, E-AUTH-BAD, E-AUTH-EXP (mint with negative expiry via a short-lived token / tampered signature). Also assert open paths (`/swagger-ui`, `/v3/api-docs`, `/actuator/health`, the 7 auth endpoints) pass **without** a token.

#### AUTH-5 · Role enforcement matrix
- IT · per route. Assert: `/plan-api/**` (non-GET-categories) and `/admin-api/**` and `/admin/worker-api/**` require `ROLE_ADMIN` (USER/WORKER → `403`, E-ROLE); `/report-api/**` allows ADMIN **and** WORKER but not USER; `GET /plan-api/categories` is public.
- Also assert E-USER-ADMIN-ROLE: a USER/WORKER token is `403` on `/user-api/report` (now ADMIN-only); `/dc-api/**` etc. stay reachable by any authenticated role by design.

#### USER-REG-1 · Register → activate → login
- IT · public. **Steps**: `POST /user-api/save` (capture `userId`; user saved `activeSw='N'`, temp password emailed) → read temp password (capture via mail `ArgumentCaptor`, **or** since the temp password isn't in the response, assert the email body contains it and drive activation from the captured value) → `POST /user-api/activate` (email + tempPassword + newPassword) → `POST /user-api/login` with newPassword.
- **Expected**: registration `200` + email sent; activation `200` "User activated successfully", `activeSw='Y'`; login succeeds. **Edge**: E-DUP-EMAIL on second save; activate with wrong tempPassword → `UnauthorizedException`; activate already-active → "already active". **Couples-to**: `user→EmailUtils`; mirror flow exists for worker (`WORKER-REG-1`, same shape).

### P1 — business rules & per-module operations

#### ELIG-MATRIX · Eligibility rule matrix *(highest bug density — Svc layer)* ✅ `EligibilityMatrixIT`, 26 tests
- **Layer** Svc (inject `EligibilityDeterminationService`; seed case+plan+income/education/children directly). One test per row; each asserts `planStatus`, `benefitAmt`, `denialReason`, **and** that an `ELIGIBILITY_DETERMINATION` row + a `CO_TRIGGERS(PENDING)` row are written.

  | Plan | Approve condition | Benefit | Deny reason |
  |------|-------------------|---------|-------------|
  | SNAP | `empIncome < 300` | 200.0 | "High Income" |
  | CCAP | `empIncome < 300` AND ≥1 child AND all children age ≤ 16 | 300.0 | "CCAP rules are not satisfied" |
  | MEDCARE | `citizenAge ≥ 65` | 350.0 | "MEDCARE rules are not satisfied" |
  | MEDAID | `empIncome < 300` AND `propertyIncome == 0.0` | 200.0 | "MEDAID rules are not satisfied" |
  | CAJW | `empIncome == 0.0` AND `passOutYear ≤ now().year` | 300.0 | "CAJW rules are not satisfied" |
  | QHP | `citizenAge ≥ 25` | **null** (approved, no benefit) | "QHP rules are not satisfied" |
  | *default* (non-keyword name) | `empIncome+propertyIncome < 10000` | `total * 0.1` | "Eligibility rules not satisfied for {name}" |
- **Edge**: E-NO-PLAN, E-NO-INCOME, E-NOTFOUND(case/plan). Missing `dob` → age computed as `0` (assert MEDCARE/QHP deny). CCAP child with null DOB counts as eligible (`?: true`) — assert.

#### DATA-1 · Data collection operations · `/dc-api/*` · IT (or Svc) ✅ `DataCollectionIT`, 11 tests
- Duplicate `loadCaseNo` throws `DuplicateResourceException` → **409** (E-CASE-DUP). (Was a plain `IllegalArgumentException` → 500; fixed.)
- `loadCaseNo` (happy → `caseNo`; **E-CASE-DUP** second call; **E-NOTFOUND** unknown appId) · `getPlanNames` (returns 6 seeded) · `updatePlanSelection` (sets `plan_id`; E-NOTFOUND case) · `saveIncome`/`saveEducation`/`saveChilds` (return generated ids; rows written) · `getDcSummary` (assembles citizen+plan+income+education+children; E-NOTFOUND case).

#### CORR-1 · Trigger processing · `GET /co-triggers-api/process` · IT ✅ `CorrespondenceIT`, 4 tests
- Happy: one `PENDING` trigger with eligibility+case+citizen present → `PROCESSED`, pdf bytes stored, one email w/ attachment. · Empty: no pending → empty list, no mail. · **Failure**: `PENDING` trigger whose eligibility row is missing → caught, trigger set `FAILED`, response `FAILED`, loop continues for others. · E-MAIL-FAIL: a hard SMTP failure now flips the trigger to `FAILED` (no longer swallowed).

#### BENEFIT-1 · Batch issuance · `GET /bi-api/send` · IT ✅ `BenefitIssuanceIT`, 5 tests
- Approved rows present → job `COMPLETED`, CSV has header + one line per approved case with `ISH-Bank` + generated account. · **No approved rows** → job `COMPLETED`, CSV header only. · Multiple approved → page size 10 honored. Note: processor result is written to CSV only, **not** back to `ELIGIBILITY_DETERMINATION` (assert DB bank/account stay null).

#### REPORT-1 · Government reports · `/report-api/government/*` · IT · ADMIN/WORKER ✅ `GovernmentReportIT`, 11 tests
- `generate` (201, `reportStatus=GENERATED`, content counts match seeded eligibility; approval-rate math: `approved/total*100`, and `0%` when total=0) · `GET ` list · `GET /{id}` (E-NOTFOUND) · `/type/{t}` · `/department?departmentName=` · `/period?periodCovered=` · `download/{id}` (content-type by format: pdf/excel/text; body = stored content) · `DELETE /{id}` (204; E-NOTFOUND).

#### CITIZEN-1 · Citizen registration · `POST /CitizenAR-api/save` · IT ✅ `CitizenRegistrationIT`, 9 tests
- Happy per state code (1–5 → correct `stateName`, row saved). **Edge**: E-SSN-LEN, E-SSN-STATE.

#### PLAN-REG-1 · Plan management · `/plan-api/*` · IT · ADMIN ✅ `PlanManagementIT`, 9 tests
- `201 CREATED`, message + `planId`, row in `PLAN_MASTER`. **Edge**: E-DUP-EMAIL-equivalent → duplicate `planName` → `DuplicateResourceException` (409); E-ROLE for non-admin.

### P2 — CRUD / administrative (terse; same template applies)

| ID | Endpoint(s) | Auth | Notes |
|----|-------------|------|-------|
| ADMIN-PLAN-CRUD | `GET /plan-api/all`, `/find/{id}`, `PUT /update`, `DELETE /delete/{id}`, `PUT /status-change/{id}/{status}` | ADMIN | happy + E-NOTFOUND + E-ROLE each |
| ADMIN-CREATE | `POST /admin-api/create` | ADMIN | creates `AdminMaster`; 409 on dup email; 400 on missing field; 🚩 bootstrap paradox (first admin needs a seed/migration) |
| ADMIN-WORKER | `POST /admin/worker-api/register`, `GET /all`, `/find/{id}`, `PUT /update`, `DELETE /delete/{id}`, `PATCH /status/{id}/{status}` | ADMIN | worker CRUD by admin |
| USER-CRUD | `GET /user-api/report`, `/find/{id}`, `PUT /update`, `DELETE /delete/{id}`, `PATCH /changeStatus/{id}/{status}` | ADMIN | USER/WORKER → `403` (E-USER-ADMIN-ROLE); ADMIN happy + E-NOTFOUND |
| WORKER-CRUD | `GET /worker-api/report`, `/find/{id}`, `PUT /update`, `DELETE /delete/{id}`, `PATCH /changeStatus/{id}/{status}` | ADMIN | same as above |
| PLAN-CAT | `GET /plan-api/categories` | public | returns seeded categories |

---

## 7. Running

```powershell
.\mvnw test                                  # whole suite (needs Docker for *IT)
.\mvnw test -Dtest=GoldenLifecycleIT          # one class
.\mvnw test -Dtest=GoldenLifecycleIT#'full insurance lifecycle from citizen registration to government report'
```

- **Docker is required** for every `…IT` (Testcontainers). Service-layer `…Test` also currently boot a
  full context + DB, so they need Docker too; split out a lighter slice profile later if that bites.
- **No CI exists** (`AGENTS.md`). When one is added, the runner must provide Docker, and `seedReference`
  + Testcontainers must run before any `…IT`.
- **No env vars or flags needed** — the `api.version` pin and case-insensitive MySQL are baked into
  `pom.xml` / `IntegrationTestBase`. First run pulls `mysql:8.0` + `testcontainers/ryuk` (~30–90s);
  later runs reuse the cached image. **Status verified: LIFE-1 + the two auth negatives pass green.**

## 8. Coverage checklist

- [x] LIFE-1 golden lifecycle (`GoldenLifecycleIT`, 3 tests)
- [x] AUTH-1..5 (`AuthIT`, 22 tests) · USER-REG-1 / WORKER-REG-1 (`UserRegistrationIT`, 5 tests) — **P0 done, 30 green**
- [x] ELIG-MATRIX (`EligibilityMatrixIT`, 26 tests — 20 rule rows + 2 side-effect + 4 not-found)
- [x] DATA-1 (`DataCollectionIT`, 11 tests)
- [x] CORR-1 (`CorrespondenceIT`, 4 tests — incl. mail-failure → FAILED)
- [x] BENEFIT-1 (`BenefitIssuanceIT`, 5 tests — incl. page-boundary + no-writeback)
- [x] REPORT-1 (`GovernmentReportIT`, 11 tests)
- [x] CITIZEN-1 (`CitizenRegistrationIT`, 9 tests — SSN→state matrix + invalid SSN)
- [x] PLAN-REG-1 (`PlanManagementIT`, 9 tests — category list + plan CRUD + status-change) — **P1 done; full suite 106 green**
- [x] P2: ADMIN-CREATE + ADMIN-WORKER (`AdminManagementIT`, 10) · USER-CRUD + WORKER-CRUD (`UserWorkerAdminCrudIT`, 7 — ADMIN-only enforced) — **P2 done; full suite 123 green**

### Findings resolved

The four 🚩 findings the suite originally pinned have since been fixed (see commit `fix: close four flagged security/correctness findings`); the tests above now assert the corrected behavior:
- **E-CASE-DUP** — duplicate `loadCaseNo` → `409` (was unmapped `500`).
- **E-MAIL-FAIL** — SMTP failure flips the trigger to `FAILED` (was silently `PROCESSED`).
- **E-CATEGORIES-PUBLIC** — `GET /plan-api/categories` reachable token-less (was `401`).
- **E-USER-ADMIN-ROLE** — `/user-api` + `/worker-api` management routes are ADMIN-only (was any authenticated role).
