package com.lakshay.healthcare

import com.lakshay.healthcare.admin.dto.PlanDataResponse
import com.lakshay.healthcare.admin.dto.PlanRequest
import com.lakshay.healthcare.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Plan + category management under /plan-api. All ADMIN-only (the role matrix lives in AuthIT); this
 * just covers the CRUD with an ADMIN bearer. Reference data seeds one category ("Health") and the
 * six keyword plans. See docs/TESTING-FLOWS.md.
 */
class PlanManagementIT : IntegrationTestBase() {

    private val seededCategoryId: Long get() = categoryRepository.findAll().first().categoryId

    @Test
    fun `PLAN-REG-1 categories lists the seeded category`() {
        mockMvc.perform(get("/plan-api/categories").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].categoryName").value(SEED_CATEGORY))
    }

    @Test
    fun `PLAN-REG-1 register creates a plan`() {
        mockMvc.perform(
            post("/plan-api/register").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        PlanRequest(
                            planName = "NEWPLAN",
                            description = "A new plan",
                            categoryId = seededCategoryId
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.message", containsString("registered successfully")))

        assertThat(planRepository.findByPlanName("NEWPLAN")).isNotNull
    }

    @Test
    fun `PLAN-REG-1 register duplicate plan name is 409`() {
        mockMvc.perform(
            post("/plan-api/register").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(PlanRequest(planName = "SNAP", categoryId = seededCategoryId))) // SNAP is seeded
        ).andExpect(status().isConflict)
    }

    @Test
    fun `PLAN-REG-1 all returns every seeded plan`() {
        mockMvc.perform(get("/plan-api/all").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(SEED_PLANS.size))
            .andExpect(jsonPath("$[*].planName", hasItem("SNAP")))
    }

    @Test
    fun `PLAN-REG-1 find by id returns the plan - unknown is 404`() {
        val id = planId("SNAP")
        mockMvc.perform(get("/plan-api/find/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.planName").value("SNAP"))

        mockMvc.perform(get("/plan-api/find/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PLAN-REG-1 update changes plan fields`() {
        val id = planId("SNAP")
        mockMvc.perform(
            put("/plan-api/update").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(PlanDataResponse(planId = id, planName = "SNAP", description = "updated desc")))
        ).andExpect(status().isOk)

        assertThat(planRepository.findById(id).get().description).isEqualTo("updated desc")
    }

    @Test
    fun `PLAN-REG-1 update unknown plan is 404`() {
        mockMvc.perform(
            put("/plan-api/update").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(PlanDataResponse(planId = 999999, planName = "GHOST")))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `PLAN-REG-1 delete removes the plan - unknown is 404`() {
        val id = planId("SNAP")
        mockMvc.perform(delete("/plan-api/delete/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
        assertThat(planRepository.findById(id)).isEmpty

        mockMvc.perform(delete("/plan-api/delete/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PLAN-REG-1 status-change flips the active switch`() {
        val id = planId("SNAP")
        mockMvc.perform(
            put("/plan-api/status-change/$id/N").header(HttpHeaders.AUTHORIZATION, adminAuth())
        ).andExpect(status().isOk)

        assertThat(planRepository.findById(id).get().activeSw).isEqualTo("N")
    }
}
