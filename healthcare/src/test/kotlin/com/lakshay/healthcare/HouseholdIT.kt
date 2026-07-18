package com.lakshay.healthcare

import com.lakshay.healthcare.data.dto.HouseholdMemberRequest
import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class HouseholdIT : IntegrationTestBase() {

    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository

    @Autowired private lateinit var dcCaseRepo: DcCaseRepository

    private fun seedCase(): Long {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = "Jane Doe",
                email = "citizen@ish.test",
                gender = "F",
                ssn = 123456704L,
                stateName = "California"
            )
        )
        return dcCaseRepo.save(DcCase(appId = app.appId)).caseNo
    }

    @Test
    fun `HOUSEHOLD adds a member and summary reflects size`() {
        val caseNo = seedCase()

        mockMvc.perform(
            post("/dc-api/saveHouseholdMember").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        HouseholdMemberRequest(
                            caseNo = caseNo,
                            fullName = "John Doe",
                            relationship = "SPOUSE",
                            dob = "1988-05-01",
                            memberIncome = 0.0
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.memberId").isNumber)

        mockMvc.perform(get("/dc-api/summary/$caseNo").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.householdSize").value(2))
            .andExpect(jsonPath("$.householdMembers.length()").value(1))
            .andExpect(jsonPath("$.householdMembers[0].relationship").value("SPOUSE"))
    }

    @Test
    fun `HOUSEHOLD add to unknown case is 404`() {
        mockMvc.perform(
            post("/dc-api/saveHouseholdMember").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(HouseholdMemberRequest(caseNo = 999999L, relationship = "SPOUSE")))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `HOUSEHOLD future dob is 400`() {
        val caseNo = seedCase()
        mockMvc.perform(
            post("/dc-api/saveHouseholdMember").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(HouseholdMemberRequest(caseNo = caseNo, relationship = "CHILD", dob = "2999-01-01")))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `HOUSEHOLD summary with no members is size 1`() {
        val caseNo = seedCase()
        mockMvc.perform(get("/dc-api/summary/$caseNo").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.householdSize").value(1))
            .andExpect(jsonPath("$.householdMembers.length()").value(0))
    }
}
