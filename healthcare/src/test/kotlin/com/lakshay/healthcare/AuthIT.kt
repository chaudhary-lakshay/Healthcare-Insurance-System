package com.lakshay.healthcare

import com.lakshay.healthcare.support.IntegrationTestBase
import com.lakshay.healthcare.user.dto.LoginRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Date

/**
 * Auth flows: user login, worker login, combined login (branching + role prefix), JWT filter
 * enforcement, and the role matrix. See docs/TESTING-FLOWS.md.
 */
class AuthIT : IntegrationTestBase() {

    private fun login(path: String, email: String, password: String) =
        mockMvc.perform(
            post(path).with(servletPath(path)).contentType(MediaType.APPLICATION_JSON)
                .content(json(LoginRequest(email, password)))
        )

    private fun loginCombined(email: String, password: String) =
        login("/api/auth/login", email, password)

    // AUTH-1: user login

    @Test
    fun `AUTH-1 user login returns ROLE_USER token`() {
        seedUser("user@ish.test", "pass123")
        login("/user-api/login", "user@ish.test", "pass123")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.role").value("ROLE_USER"))
            .andExpect(jsonPath("$.userId").isNumber)
    }

    @Test
    fun `AUTH-1 user login wrong password is 401`() {
        seedUser("user@ish.test", "pass123")
        login("/user-api/login", "user@ish.test", "wrong").andExpect(status().isUnauthorized)
    }

    @Test
    fun `AUTH-1 user login before activation is 401`() {
        seedUser("pending@ish.test", "pass123", active = false)
        login("/user-api/login", "pending@ish.test", "pass123").andExpect(status().isUnauthorized)
    }

    @Test
    fun `AUTH-1 user login unknown email is 401`() {
        login("/user-api/login", "nobody@ish.test", "pass123").andExpect(status().isUnauthorized)
    }

    // AUTH-2: worker login

    @Test
    fun `AUTH-2 worker login returns ROLE_WORKER token`() {
        seedWorker("worker@ish.test", "pass123")
        login("/worker-api/login", "worker@ish.test", "pass123")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ROLE_WORKER"))
            .andExpect(jsonPath("$.workerId").isNumber)
    }

    @Test
    fun `AUTH-2 worker login before activation is 401`() {
        seedWorker("pendingworker@ish.test", "pass123", active = false)
        login("/worker-api/login", "pendingworker@ish.test", "pass123").andExpect(status().isUnauthorized)
    }

    // AUTH-3: combined login branching + role prefix

    @Test
    fun `AUTH-3 combined login resolves admin and mints ROLE_ADMIN claim`() {
        // admin@ish.test / admin123 comes from seedReference().
        val result = loginCombined(ADMIN_EMAIL, ADMIN_PASSWORD)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("ADMIN"))
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andReturn()
        val token = objectMapper.readTree(result.response.contentAsString).get("token").asText()
        // role claim is already ROLE_ADMIN; the filter uses it verbatim so hasRole("ADMIN") matches
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_ADMIN")
    }

    @Test
    fun `AUTH-3 combined login resolves worker`() {
        seedWorker("worker@ish.test", "pass123")
        loginCombined("worker@ish.test", "pass123")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("WORKER"))
    }

    @Test
    fun `AUTH-3 combined login resolves user`() {
        seedUser("user@ish.test", "pass123")
        loginCombined("user@ish.test", "pass123")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("USER"))
    }

    @Test
    fun `AUTH-3 admin login skips activeSw check unlike user-worker`() {
        // Admin seeded active; the combined endpoint never checks activeSw for the admin branch.
        // Contrast: a deactivated USER cannot log in (covered in AUTH-1).
        loginCombined(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk)
    }

    // AUTH-4: JWT filter enforcement

    private val protectedPath = "/report-api/government"

    @Test
    fun `AUTH-4 no token is 401`() {
        mockMvc.perform(get(protectedPath)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `AUTH-4 malformed token is 401`() {
        mockMvc.perform(get(protectedPath).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `AUTH-4 non-bearer scheme is 401`() {
        mockMvc.perform(get(protectedPath).header(HttpHeaders.AUTHORIZATION, "Basic abc"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `AUTH-4 expired token is 401`() {
        val expired = forgeToken(role = "ROLE_WORKER", expiry = Date(System.currentTimeMillis() - 1000))
        mockMvc.perform(get(protectedPath).header(HttpHeaders.AUTHORIZATION, "Bearer $expired"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `AUTH-4 wrong audience is 401`() {
        val badAud = forgeToken(role = "ROLE_WORKER", audience = "someone-else")
        mockMvc.perform(get(protectedPath).header(HttpHeaders.AUTHORIZATION, "Bearer $badAud"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `AUTH-4 wrong issuer is 401`() {
        val badIss = forgeToken(role = "ROLE_WORKER", issuer = "evil")
        mockMvc.perform(get(protectedPath).header(HttpHeaders.AUTHORIZATION, "Bearer $badIss"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `AUTH-4 open path needs no token`() {
        // /actuator/health is permitAll, no token needed
        mockMvc.perform(get("/actuator/health").with(servletPath("/actuator/health")))
            .andExpect(status().isOk)
    }

    @Test
    fun `AUTH-4 categories is reachable without a token`() {
        // GET /plan-api/categories is permitAll, so the public category lookup needs no token
        mockMvc.perform(get("/plan-api/categories").with(servletPath("/plan-api/categories")))
            .andExpect(status().isOk)
    }

    // AUTH-5: role matrix

    @Test
    fun `AUTH-5 admin-only route allows ADMIN`() {
        mockMvc.perform(get("/plan-api/all").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
            .andExpect(status().isOk)
    }

    @Test
    fun `AUTH-5 admin-only route forbids USER and WORKER`() {
        mockMvc.perform(get("/plan-api/all").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_USER")))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/plan-api/all").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_WORKER")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `AUTH-5 report route allows ADMIN and WORKER but not USER`() {
        mockMvc.perform(get(protectedPath).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
            .andExpect(status().isOk)
        mockMvc.perform(get(protectedPath).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_WORKER")))
            .andExpect(status().isOk)
        mockMvc.perform(get(protectedPath).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_USER")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `AUTH-5 user-api admin routes are ADMIN-only`() {
        // /user-api management routes (report/find/update/delete/status) require ADMIN; a plain USER
        // or WORKER is forbidden. The public save/activate/login routes stay open (covered elsewhere).
        mockMvc.perform(get("/user-api/report").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_USER")))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/user-api/report").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_WORKER")))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/user-api/report").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
            .andExpect(status().isOk)
    }
}
