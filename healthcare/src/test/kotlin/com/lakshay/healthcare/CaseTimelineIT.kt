package com.lakshay.healthcare

import com.lakshay.healthcare.eligibility.service.EligibilityDeterminationService
import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.entity.DcIncome
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.DcIncomeRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Envers revision capture + the citizen-facing timeline endpoint (issue #13).
class CaseTimelineIT : IntegrationTestBase() {

    @Autowired lateinit var eligibilityService: EligibilityDeterminationService
    @Autowired lateinit var citizenRepo: CitizenAppRegistrationRepository
    @Autowired lateinit var dcCaseRepo: DcCaseRepository
    @Autowired lateinit var dcIncomeRepo: DcIncomeRepository

    private val ownerEmail = "timeline@ish.test"

    private fun seedCase(): Long {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = "Tim Line", email = ownerEmail, gender = "M",
                ssn = 123456705L, stateName = "California"
            )
        )
        val case = dcCaseRepo.save(DcCase(appId = app.appId, planId = planId("SNAP")))
        dcIncomeRepo.save(DcIncome(caseNo = case.caseNo, empIncome = 100.0))
        return case.caseNo
    }

    @Test
    fun `timeline shows SUBMITTED then DETERMINED for the owner`() {
        val caseNo = seedCase()
        eligibilityService.determineEligibility(caseNo)

        mockMvc.perform(
            get("/CitizenAR-api/timeline/$caseNo")
                .header("Authorization", bearer("ROLE_CITIZEN", ownerEmail))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.caseNo").value(caseNo))
            .andExpect(jsonPath("$.timeline[0].status").value("SUBMITTED"))
            .andExpect(jsonPath("$.timeline[1].status").value("DETERMINED"))
    }

    @Test
    fun `staff can read any timeline`() {
        val caseNo = seedCase()
        mockMvc.perform(
            get("/CitizenAR-api/timeline/$caseNo").header("Authorization", adminAuth())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timeline[0].status").value("SUBMITTED"))
    }

    @Test
    fun `non-owner citizen gets uniform 403`() {
        val caseNo = seedCase()
        mockMvc.perform(
            get("/CitizenAR-api/timeline/$caseNo")
                .header("Authorization", bearer("ROLE_CITIZEN", "someone.else@ish.test"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `no token is 401`() {
        val caseNo = seedCase()
        mockMvc.perform(get("/CitizenAR-api/timeline/$caseNo").with(servletPath("/CitizenAR-api/timeline/$caseNo")))
            .andExpect(status().isUnauthorized)
    }
}
