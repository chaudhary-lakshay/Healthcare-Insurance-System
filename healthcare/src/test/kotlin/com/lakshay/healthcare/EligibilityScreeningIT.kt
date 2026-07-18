package com.lakshay.healthcare

import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.entity.DcIncome
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.DcIncomeRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class EligibilityScreeningIT : IntegrationTestBase() {

    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository

    @Autowired private lateinit var dcCaseRepo: DcCaseRepository

    @Autowired private lateinit var dcIncomeRepo: DcIncomeRepository

    private fun seedCaseWithIncome(email: String, empIncome: Double? = 100.0): Long {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = "Jane Doe",
                email = email,
                gender = "F",
                ssn = 123456704L,
                stateName = "California"
            )
        )
        val caseNo = dcCaseRepo.save(DcCase(appId = app.appId)).caseNo
        if (empIncome != null) dcIncomeRepo.save(DcIncome(caseNo = caseNo, empIncome = empIncome, propertyIncome = 0.0))
        return caseNo
    }

    @Test
    fun `staff screens a case across all active programs`() {
        val caseNo = seedCaseWithIncome("c@ish.test", 100.0)
        mockMvc.perform(get("/ed-api/screen/$caseNo").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.caseNo").value(caseNo))
            .andExpect(jsonPath("$.programs.length()").value(SEED_PLANS.size))
            .andExpect(jsonPath("$.programs[?(@.planName=='SNAP')].planStatus", hasItem("APPROVED")))
            .andExpect(jsonPath("$.programs[0].holderSSN").doesNotExist())
    }

    @Test
    fun `citizen screens own case`() {
        val caseNo = seedCaseWithIncome("owner@ish.test", 100.0)
        mockMvc.perform(
            get("/ed-api/screen/$caseNo")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_CITIZEN", "owner@ish.test"))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `citizen cannot screen another citizens case`() {
        val caseNo = seedCaseWithIncome("owner@ish.test", 100.0)
        mockMvc.perform(
            get("/ed-api/screen/$caseNo")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_CITIZEN", "intruder@ish.test"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `screening a case with no income is 404`() {
        val caseNo = seedCaseWithIncome("c@ish.test", null)
        mockMvc.perform(get("/ed-api/screen/$caseNo").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `screening needs a token`() {
        val caseNo = seedCaseWithIncome("c@ish.test", 100.0)
        mockMvc.perform(get("/ed-api/screen/$caseNo")).andExpect(status().isUnauthorized)
    }
}
