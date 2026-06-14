package com.lakshay.healthcare

import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.entity.EligibilityDetails
import com.lakshay.healthcare.shared.entity.UserMaster
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import com.lakshay.healthcare.user.dto.LoginRequest
import com.lakshay.healthcare.user.dto.RegisterRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CitizenPortalIT : IntegrationTestBase() {

    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository
    @Autowired private lateinit var dcCaseRepo: DcCaseRepository
    @Autowired private lateinit var eligibilityRepo: EligibilityDetailsRepository

    private fun seedCaseFor(email: String): Long {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = "Jane Doe", email = email, gender = "F",
                ssn = 123456704L, stateName = "California"
            )
        )
        val caseNo = dcCaseRepo.save(DcCase(appId = app.appId)).caseNo
        eligibilityRepo.save(
            EligibilityDetails(
                caseNo = caseNo, holderName = "Jane Doe",
                planName = "SNAP", planStatus = "APPROVED", benefitAmt = 200.0
            )
        )
        return caseNo
    }

    @Test
    fun `CITIZEN sees own case status and no ssn`() {
        val caseNo = seedCaseFor("citizen@ish.test")
        mockMvc.perform(
            get("/citizen-api/cases/$caseNo/status")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_CITIZEN", "citizen@ish.test"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.caseNo").value(caseNo))
            .andExpect(jsonPath("$.planStatus").value("APPROVED"))
            .andExpect(jsonPath("$.ssn").doesNotExist())
            .andExpect(jsonPath("$.holderSSN").doesNotExist())
    }

    @Test
    fun `CITIZEN cannot see another citizens case`() {
        val caseNo = seedCaseFor("owner@ish.test")
        mockMvc.perform(
            get("/citizen-api/cases/$caseNo/status")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_CITIZEN", "intruder@ish.test"))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `staff can see any case`() {
        val caseNo = seedCaseFor("someone@ish.test")
        mockMvc.perform(
            get("/citizen-api/cases/$caseNo/status").header(HttpHeaders.AUTHORIZATION, adminAuth())
        ).andExpect(status().isOk)
    }

    @Test
    fun `unknown case is forbidden for a citizen so existence does not leak`() {
        mockMvc.perform(
            get("/citizen-api/cases/999999/status")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_CITIZEN", "citizen@ish.test"))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `case status needs a token`() {
        val caseNo = seedCaseFor("citizen@ish.test")
        mockMvc.perform(get("/citizen-api/cases/$caseNo/status")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `citizen registration creates an inactive CITIZEN account`() {
        mockMvc.perform(
            post("/citizen-api/register").with(servletPath("/citizen-api/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(RegisterRequest(name = "New Citizen", password = "ignored", email = "newcit@ish.test", gender = "F")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").isNumber)
        val u = userRepository.findByEmail("newcit@ish.test")!!
        assertThat(u.role).isEqualTo("CITIZEN")
        assertThat(u.activeSw).isEqualTo("N")
    }

    @Test
    fun `activated citizen logs in with ROLE_CITIZEN`() {
        userRepository.save(
            UserMaster(
                name = "Active Citizen", password = passwordEncoder.encode("pass123"),
                email = "active.cit@ish.test", gender = "F", activeSw = "Y", role = "CITIZEN"
            )
        )
        mockMvc.perform(
            post("/user-api/login").with(servletPath("/user-api/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(LoginRequest("active.cit@ish.test", "pass123")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ROLE_CITIZEN"))
    }
}
