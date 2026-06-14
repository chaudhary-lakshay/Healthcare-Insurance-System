package com.lakshay.healthcare.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.lakshay.healthcare.shared.entity.AdminMaster
import com.lakshay.healthcare.shared.entity.Plan
import com.lakshay.healthcare.shared.entity.PlanCategory
import com.lakshay.healthcare.shared.entity.UserMaster
import com.lakshay.healthcare.shared.entity.WorkerMaster
import com.lakshay.healthcare.shared.repository.AdminMasterRepository
import com.lakshay.healthcare.shared.repository.PlanCategoryRepository
import com.lakshay.healthcare.shared.repository.PlanRepository
import com.lakshay.healthcare.shared.repository.UserMasterRepository
import com.lakshay.healthcare.shared.repository.WorkerMasterRepository
import com.lakshay.healthcare.shared.security.JwtUtil
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.BeforeEach
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.testcontainers.containers.MySQLContainer
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * Base for the integration tests: real Spring context + MockMvc through the whole filter chain
 * (so JwtAuthFilter actually runs) against a real MySQL 8 container. Flyway runs the prod
 * migrations and ddl-auto: validate checks the entities match — same deal as prod.
 *
 * Per test: resetDb truncates the domain tables, seedReference re-adds the bare minimum the app
 * can't boot without (an admin to log in as, a plan category, the keyword plans the rule engine
 * switches on). State is committed for real — the batch job and multi-request flows need it; a
 * @Transactional rollback wouldn't survive the job's own tx.
 *
 * Mail is mocked; createMimeMessage() returns a real MimeMessage so EmailUtils builds fine and
 * tests can capture what got sent. bearer() mints a real token via JwtUtil — @WithMockUser won't
 * work since the filter wants a real Bearer header.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    companion object {
        // One MySQL for the whole JVM, started once, never stopped (Ryuk reaps it at exit). Not
        // @Testcontainers/@Container — that stops the static container per class and the next IT
        // class hits "Connection refused". @ServiceConnection still wires spring.datasource.*.
        //
        // --lower-case-table-names=1: Linux MySQL is case-sensitive, but the migrations make
        // UPPER_CASE tables while Hibernate looks them up lower-cased. Matches Windows dev/prod and
        // keeps ddl-auto: validate happy.
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withCommand("--lower-case-table-names=1")

        init {
            mysql.start()
        }

        const val ADMIN_EMAIL = "admin@ish.test"
        const val ADMIN_PASSWORD = "admin123"
        const val SEED_CATEGORY = "Health"
        val SEED_PLANS = listOf("SNAP", "CCAP", "MEDCARE", "MEDAID", "CAJW", "QHP")
        val BENEFIT_OUTPUT_FILE = File("target/benefit_output_test.csv")

        // Order matters only conceptually; FK checks are disabled around the truncate.
        private val DOMAIN_TABLES = listOf(
            "CO_TRIGGERS", "ELIGIBILITY_DETERMINATION", "DC_CHILDREN", "DC_EDUCATION",
            "DC_INCOME", "HOUSEHOLD_MEMBER", "DC_CASES", "CITIZEN_APPLICATION", "ISH_GOVERNMENT_REPORTS",
            "PLAN_MASTER", "PLAN_CATEGORY", "USER_MASTER", "WORKER_MASTER", "ADMIN_MASTER", "NOTICE"
        )
    }

    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var objectMapper: ObjectMapper
    @Autowired protected lateinit var jwtUtil: JwtUtil
    @Autowired protected lateinit var jdbc: JdbcTemplate
    @Autowired protected lateinit var passwordEncoder: PasswordEncoder
    @Autowired protected lateinit var adminRepository: AdminMasterRepository
    @Autowired protected lateinit var categoryRepository: PlanCategoryRepository
    @Autowired protected lateinit var planRepository: PlanRepository
    @Autowired protected lateinit var userRepository: UserMasterRepository
    @Autowired protected lateinit var workerRepository: WorkerMasterRepository

    @Value("\${jwt.secret}") protected lateinit var jwtSecret: String
    @Value("\${jwt.issuer}") protected lateinit var jwtIssuer: String
    @Value("\${jwt.audience}") protected lateinit var jwtAudience: String

    @MockBean protected lateinit var mailSender: JavaMailSender

    @BeforeEach
    fun setUpBaseFixture() {
        // Real MimeMessage so EmailUtils' MimeMessageHelper works; send(...) itself stays a no-op mock.
        given(mailSender.createMimeMessage()).willReturn(JavaMailSenderImpl().createMimeMessage())
        BENEFIT_OUTPUT_FILE.delete()
        resetDb()
        seedReference()
    }

    protected fun resetDb() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0")
        DOMAIN_TABLES.forEach { jdbc.execute("TRUNCATE TABLE $it") }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1")
    }

    /** Reference data every test can assume exists. Tests add their own transactional data on top. */
    protected fun seedReference() {
        adminRepository.save(
            AdminMaster(
                name = "Seed Admin",
                email = ADMIN_EMAIL,
                password = passwordEncoder.encode(ADMIN_PASSWORD),
                role = "ADMIN",
                activeSw = "Y"
            )
        )
        val category = categoryRepository.save(PlanCategory(categoryName = SEED_CATEGORY))
        SEED_PLANS.forEach { name ->
            planRepository.save(Plan(planName = name, categoryId = category.categoryId))
        }
    }

    protected fun planId(planName: String): Long =
        planRepository.findByPlanName(planName)?.planId
            ?: error("Seed plan $planName missing")

    /** `Authorization` header value carrying a freshly minted token for [role] (e.g. "ROLE_ADMIN"). */
    protected fun bearer(role: String, email: String = ADMIN_EMAIL): String =
        "Bearer " + jwtUtil.generateToken(email, role)

    protected fun adminAuth(): String = bearer("ROLE_ADMIN")

    protected fun json(value: Any): String = objectMapper.writeValueAsString(value)

    /**
     * `JwtAuthFilter` decides open paths via `request.servletPath`, which MockMvc leaves blank — so
     * token-less open endpoints (login/register/activate) 401 unless we populate it. Real Tomcat sets
     * it; this RequestPostProcessor mirrors that. Only needed on token-less requests.
     */
    protected fun servletPath(path: String): RequestPostProcessor =
        RequestPostProcessor { req -> req.servletPath = path; req }

    /** Seed an activated USER with a bcrypt-hashed [rawPassword]. Returns the saved entity. */
    protected fun seedUser(
        email: String,
        rawPassword: String,
        active: Boolean = true
    ): UserMaster = userRepository.save(
        UserMaster(
            name = "Test User",
            password = passwordEncoder.encode(rawPassword),
            email = email,
            gender = "F",
            activeSw = if (active) "Y" else "N",
            role = "USER"
        )
    )

    /** Seed an activated WORKER with a bcrypt-hashed [rawPassword]. Returns the saved entity. */
    protected fun seedWorker(
        email: String,
        rawPassword: String,
        active: Boolean = true
    ): WorkerMaster = workerRepository.save(
        WorkerMaster(
            name = "Test Worker",
            password = passwordEncoder.encode(rawPassword),
            email = email,
            gender = "M",
            designation = "Caseworker",
            activeSw = if (active) "Y" else "N",
            role = "WORKER"
        )
    )

    /**
     * Build a signed JWT with arbitrary claims/expiry using the same key as [JwtUtil] — for forging
     * expired / wrong-issuer / wrong-audience tokens that the real `generateToken` can't produce.
     */
    protected fun forgeToken(
        email: String = ADMIN_EMAIL,
        role: String = "ROLE_ADMIN",
        issuer: String = jwtIssuer,
        audience: String = jwtAudience,
        expiry: Date = Date(System.currentTimeMillis() + 60_000)
    ): String = Jwts.builder()
        .setSubject(email)
        .claim("role", role)
        .setIssuer(issuer)
        .setAudience(audience)
        .setIssuedAt(Date())
        .setExpiration(expiry)
        .signWith(Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8)))
        .compact()
}
