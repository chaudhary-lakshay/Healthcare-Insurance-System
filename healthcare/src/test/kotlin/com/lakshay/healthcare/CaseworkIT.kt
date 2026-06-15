package com.lakshay.healthcare

import com.lakshay.healthcare.casework.dto.CaseNoteRequest
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

class CaseworkIT : IntegrationTestBase() {

    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository
    @Autowired private lateinit var dcCaseRepo: DcCaseRepository

    private fun seedCase(name: String = "Jane Doe"): Long {
        val app = citizenRepo.save(
            CitizenAppRegistration(fullName = name, email = "c@ish.test", gender = "F", ssn = 123456704L, stateName = "California")
        )
        return dcCaseRepo.save(DcCase(appId = app.appId)).caseNo
    }

    @Test
    fun `staff adds and lists case notes`() {
        val caseNo = seedCase()
        mockMvc.perform(
            post("/casework-api/cases/$caseNo/notes").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(CaseNoteRequest("called citizen, awaiting docs")))
        ).andExpect(status().isOk).andExpect(jsonPath("$.noteId").isNumber)

        mockMvc.perform(get("/casework-api/cases/$caseNo/notes").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].body").value("called citizen, awaiting docs"))
    }

    @Test
    fun `queue lists submitted cases with citizen name`() {
        val caseNo = seedCase("Queue Citizen")
        mockMvc.perform(get("/casework-api/queue?status=SUBMITTED").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].caseNo").value(caseNo))
            .andExpect(jsonPath("$[0].caseStatus").value("SUBMITTED"))
            .andExpect(jsonPath("$[0].citizenName").value("Queue Citizen"))
    }

    @Test
    fun `worker can access casework`() {
        val caseNo = seedCase()
        mockMvc.perform(get("/casework-api/cases/$caseNo/notes").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_WORKER")))
            .andExpect(status().isOk)
    }

    @Test
    fun `citizen is forbidden from casework`() {
        val caseNo = seedCase()
        mockMvc.perform(get("/casework-api/cases/$caseNo/notes").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_CITIZEN", "c@ish.test")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `queue with a bad status is 400`() {
        mockMvc.perform(get("/casework-api/queue?status=NONSENSE").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `note on unknown case is 404`() {
        mockMvc.perform(
            post("/casework-api/cases/999999/notes").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(CaseNoteRequest("x")))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `casework needs a token`() {
        mockMvc.perform(get("/casework-api/queue")).andExpect(status().isUnauthorized)
    }
}
