package com.lakshay.healthcare

import com.lakshay.healthcare.admin.dto.WorkerDataResponse
import com.lakshay.healthcare.support.IntegrationTestBase
import com.lakshay.healthcare.user.dto.RegisterRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * ADMIN-only endpoints (/admin-api/create, /admin/worker-api), driven with the seeded ADMIN bearer.
 * See docs/TESTING-FLOWS.md.
 *
 * Chicken-and-egg: creating an admin needs an admin token, so the first admin has to come from a
 * seed/migration — here it's seedReference()'s admin@ish.test.
 */
class AdminManagementIT : IntegrationTestBase() {

    private fun createAdminBody(vararg pairs: Pair<String, String>) = json(mapOf(*pairs))

    // ADMIN-CREATE: POST /admin-api/create

    @Test
    fun `ADMIN-CREATE creates a new admin`() {
        mockMvc.perform(
            post("/admin-api/create").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    createAdminBody("email" to "new.admin@ish.test", "name" to "New Admin", "password" to "pass123")
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.adminId").isNumber)
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.message").value("Admin created successfully"))

        assertThat(adminRepository.findByEmail("new.admin@ish.test")).isNotNull
    }

    @Test
    fun `ADMIN-CREATE duplicate email is 409`() {
        mockMvc.perform(
            post("/admin-api/create").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createAdminBody("email" to ADMIN_EMAIL, "name" to "Dupe", "password" to "pass123"))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `ADMIN-CREATE missing password is 400`() {
        mockMvc.perform(
            post("/admin-api/create").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createAdminBody("email" to "x@ish.test", "name" to "No Password"))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `ADMIN-CREATE forbids non-admin role`() {
        mockMvc.perform(
            post("/admin-api/create").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createAdminBody("email" to "y@ish.test", "name" to "Blocked", "password" to "pass123"))
        ).andExpect(status().isForbidden)
    }

    // ADMIN-WORKER: /admin/worker-api

    @Test
    fun `ADMIN-WORKER register creates a worker`() {
        mockMvc.perform(
            post("/admin/worker-api/register").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        RegisterRequest(
                            name = "Wendy Worker",
                            password = "ignored",
                            email = "wendy@ish.test",
                            gender = "F",
                            designation = "Caseworker"
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.workerId").isNumber)

        assertThat(workerRepository.findByEmail("wendy@ish.test")).isNotNull
    }

    @Test
    fun `ADMIN-WORKER all lists workers`() {
        seedWorker("w1@ish.test", "pass123")
        seedWorker("w2@ish.test", "pass123")

        mockMvc.perform(get("/admin/worker-api/all").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `ADMIN-WORKER find by id - unknown is 404`() {
        val id = seedWorker("w@ish.test", "pass123").workerId
        mockMvc.perform(get("/admin/worker-api/find/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("w@ish.test"))

        mockMvc.perform(get("/admin/worker-api/find/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `ADMIN-WORKER update changes worker fields`() {
        val id = seedWorker("w@ish.test", "pass123").workerId
        mockMvc.perform(
            put("/admin/worker-api/update").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(WorkerDataResponse(workerId = id, name = "Renamed", gender = "M", designation = "Lead")))
        ).andExpect(status().isOk)

        assertThat(workerRepository.findById(id).get().name).isEqualTo("Renamed")
    }

    @Test
    fun `ADMIN-WORKER delete removes worker - unknown is 404`() {
        val id = seedWorker("w@ish.test", "pass123").workerId
        mockMvc.perform(delete("/admin/worker-api/delete/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
        assertThat(workerRepository.findById(id)).isEmpty

        mockMvc.perform(delete("/admin/worker-api/delete/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `ADMIN-WORKER status change flips active switch`() {
        val id = seedWorker("w@ish.test", "pass123").workerId
        mockMvc.perform(
            patch("/admin/worker-api/status/$id/N").header(HttpHeaders.AUTHORIZATION, adminAuth())
        ).andExpect(status().isOk)

        assertThat(workerRepository.findById(id).get().activeSw).isEqualTo("N")
    }
}
