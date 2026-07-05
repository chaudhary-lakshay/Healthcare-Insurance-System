package com.lakshay.healthcare

import com.lakshay.healthcare.casework.dto.CaseNoteRequest
import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CaseworkIT : IntegrationTestBase() {

    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository
    @Autowired private lateinit var dcCaseRepo: DcCaseRepository
    @Autowired private lateinit var noticeRepo: com.lakshay.healthcare.shared.repository.NoticeRepository
    @Autowired private lateinit var documentRepo: com.lakshay.healthcare.shared.repository.DocumentRepository

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

    @Test
    fun `staff assigns a case to an active worker and reads it back`() {
        val caseNo = seedCase()
        seedWorker("wk@ish.test", "pass123")
        mockMvc.perform(
            put("/casework-api/cases/$caseNo/assignment").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.AssignmentRequest("wk@ish.test")))
        ).andExpect(status().isOk).andExpect(jsonPath("$.assignedTo").value("wk@ish.test"))

        mockMvc.perform(get("/casework-api/cases/$caseNo/assignment").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk).andExpect(jsonPath("$.assignedTo").value("wk@ish.test"))
    }

    @Test
    fun `reassigning a case updates the assignee`() {
        val caseNo = seedCase()
        seedWorker("w1@ish.test", "p")
        seedWorker("w2@ish.test", "p")
        mockMvc.perform(
            put("/casework-api/cases/$caseNo/assignment").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.AssignmentRequest("w1@ish.test")))
        ).andExpect(status().isOk)
        mockMvc.perform(
            put("/casework-api/cases/$caseNo/assignment").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.AssignmentRequest("w2@ish.test")))
        ).andExpect(status().isOk).andExpect(jsonPath("$.assignedTo").value("w2@ish.test"))
    }

    @Test
    fun `assigning to a non-worker is 400`() {
        val caseNo = seedCase()
        mockMvc.perform(
            put("/casework-api/cases/$caseNo/assignment").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.AssignmentRequest("nobody@ish.test")))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `assigning an unknown case is 404`() {
        seedWorker("wk@ish.test", "p")
        mockMvc.perform(
            put("/casework-api/cases/999999/assignment").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.AssignmentRequest("wk@ish.test")))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `getting an assignment that does not exist is 404`() {
        val caseNo = seedCase()
        mockMvc.perform(get("/casework-api/cases/$caseNo/assignment").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `RFI notifies the citizen and reports it sent`() {
        val caseNo = seedCase()
        mockMvc.perform(
            post("/casework-api/cases/$caseNo/rfi").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.RfiRequest("please upload proof of income")))
        ).andExpect(status().isOk).andExpect(jsonPath("$.notificationSent").value(true))

        assertThat(noticeRepo.findByRecipientOrderByCreatedAtDesc("c@ish.test").any { it.noticeType == "RFI" }).isTrue
    }

    @Test
    fun `RFI with an SSN in the message is 400`() {
        val caseNo = seedCase()
        mockMvc.perform(
            post("/casework-api/cases/$caseNo/rfi").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.RfiRequest("confirm ssn 123-45-6789")))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `RFI on a case with no citizen on file is 404`() {
        val caseNo = dcCaseRepo.save(com.lakshay.healthcare.shared.entity.DcCase(appId = 99999L)).caseNo
        mockMvc.perform(
            post("/casework-api/cases/$caseNo/rfi").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.RfiRequest("hello")))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `staff lists and reviews a case document`() {
        val caseNo = seedCase()
        val docId = documentRepo.save(
            com.lakshay.healthcare.shared.entity.Document(caseNo = caseNo, uploadedBy = "c@ish.test", docType = "ID", contentType = "application/pdf", content = byteArrayOf(1, 2, 3))
        ).docId

        mockMvc.perform(get("/casework-api/cases/$caseNo/documents").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("UPLOADED"))

        mockMvc.perform(
            post("/casework-api/documents/$docId/review").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.DocumentReviewRequest("REVIEWED")))
        ).andExpect(status().isOk).andExpect(jsonPath("$.status").value("REVIEWED"))

        assertThat(documentRepo.findById(docId).get().status).isEqualTo("REVIEWED")
    }

    @Test
    fun `review with a bad decision is 400`() {
        val caseNo = seedCase()
        val docId = documentRepo.save(
            com.lakshay.healthcare.shared.entity.Document(caseNo = caseNo, uploadedBy = "x", docType = "ID", content = byteArrayOf(1))
        ).docId
        mockMvc.perform(
            post("/casework-api/documents/$docId/review").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.DocumentReviewRequest("MAYBE")))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `reviewing an unknown document is 404`() {
        mockMvc.perform(
            post("/casework-api/documents/999999/review").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON).content(json(com.lakshay.healthcare.casework.dto.DocumentReviewRequest("REVIEWED")))
        ).andExpect(status().isNotFound)
    }
}
