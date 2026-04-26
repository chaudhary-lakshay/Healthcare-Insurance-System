package com.lakshay.healthcare

import com.lakshay.healthcare.admin.dto.UserDataResponse
import com.lakshay.healthcare.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Admin user/worker management on /user-api and /worker-api (report/find/update/delete/changeStatus).
 * These are ADMIN-only — SecurityConfig locks the user-api/worker-api prefixes to ADMIN, the public
 * save/activate/login routes match permitAll first. Each test checks a plain USER is forbidden, then
 * runs it as ADMIN. See docs/TESTING-FLOWS.md.
 */
class UserWorkerAdminCrudIT : IntegrationTestBase() {

    private val userToken get() = bearer("ROLE_USER")

    // USER-CRUD via /user-api

    @Test
    fun `USER-CRUD list users - USER forbidden, ADMIN ok`() {
        seedUser("u1@ish.test", "pass123")

        mockMvc.perform(get("/user-api/report").header(HttpHeaders.AUTHORIZATION, userToken))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/user-api/report").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `USER-CRUD find by id - USER forbidden, ADMIN ok, unknown 404`() {
        val id = seedUser("u@ish.test", "pass123").userId

        mockMvc.perform(get("/user-api/find/$id").header(HttpHeaders.AUTHORIZATION, userToken))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/user-api/find/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("u@ish.test"))

        mockMvc.perform(get("/user-api/find/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `USER-CRUD update - USER forbidden, ADMIN ok`() {
        val id = seedUser("u@ish.test", "pass123").userId

        mockMvc.perform(
            put("/user-api/update").header(HttpHeaders.AUTHORIZATION, userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(UserDataResponse(userId = id, name = "Hacked", gender = "M")))
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            put("/user-api/update").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(UserDataResponse(userId = id, name = "Renamed User", gender = "M")))
        ).andExpect(status().isOk)

        assertThat(userRepository.findById(id).get().name).isEqualTo("Renamed User")
    }

    @Test
    fun `USER-CRUD change status and delete - USER forbidden, ADMIN ok`() {
        val id = seedUser("u@ish.test", "pass123").userId

        mockMvc.perform(patch("/user-api/changeStatus/$id/N").header(HttpHeaders.AUTHORIZATION, userToken))
            .andExpect(status().isForbidden)
        mockMvc.perform(delete("/user-api/delete/$id").header(HttpHeaders.AUTHORIZATION, userToken))
            .andExpect(status().isForbidden)

        mockMvc.perform(patch("/user-api/changeStatus/$id/N").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
        assertThat(userRepository.findById(id).get().activeSw).isEqualTo("N")

        mockMvc.perform(delete("/user-api/delete/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
        assertThat(userRepository.findById(id)).isEmpty
    }

    // WORKER-CRUD via /worker-api

    @Test
    fun `WORKER-CRUD list workers - USER forbidden, ADMIN ok`() {
        seedWorker("wk1@ish.test", "pass123")

        mockMvc.perform(get("/worker-api/report").header(HttpHeaders.AUTHORIZATION, userToken))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/worker-api/report").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `WORKER-CRUD find by id - USER forbidden, ADMIN ok, unknown 404`() {
        val id = seedWorker("wk@ish.test", "pass123").workerId

        mockMvc.perform(get("/worker-api/find/$id").header(HttpHeaders.AUTHORIZATION, userToken))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/worker-api/find/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("wk@ish.test"))

        mockMvc.perform(get("/worker-api/find/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `WORKER-CRUD change status and delete - USER forbidden, ADMIN ok`() {
        val id = seedWorker("wk@ish.test", "pass123").workerId

        mockMvc.perform(patch("/worker-api/changeStatus/$id/N").header(HttpHeaders.AUTHORIZATION, userToken))
            .andExpect(status().isForbidden)
        mockMvc.perform(delete("/worker-api/delete/$id").header(HttpHeaders.AUTHORIZATION, userToken))
            .andExpect(status().isForbidden)

        mockMvc.perform(patch("/worker-api/changeStatus/$id/N").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
        assertThat(workerRepository.findById(id).get().activeSw).isEqualTo("N")

        mockMvc.perform(delete("/worker-api/delete/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
        assertThat(workerRepository.findById(id)).isEmpty
    }
}
