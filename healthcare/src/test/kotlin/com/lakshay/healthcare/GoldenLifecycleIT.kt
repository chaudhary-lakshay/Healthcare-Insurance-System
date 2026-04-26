package com.lakshay.healthcare

import com.lakshay.healthcare.application.dto.CitizenRegistrationRequest
import com.lakshay.healthcare.data.dto.IncomeRequest
import com.lakshay.healthcare.data.dto.PlanSelectionRequest
import com.lakshay.healthcare.report.dto.ReportRequest
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.CoTriggerRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import jakarta.mail.internet.MimeMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The golden end-to-end flow: one committed run that touches every cross-module seam. Other flow
 * tests copy this shape. Phases line up with docs/TESTING-FLOWS.md.
 *
 * citizen (SSN validate) -> data collection -> eligibility (EligibilityDetails + PENDING CoTrigger)
 * -> correspondence (PDF + email, mocked) -> benefit batch (APPROVED -> CSV) -> govt report.
 */
class GoldenLifecycleIT : IntegrationTestBase() {

    @Autowired lateinit var citizenRepository: CitizenAppRegistrationRepository
    @Autowired lateinit var eligibilityRepository: EligibilityDetailsRepository
    @Autowired lateinit var coTriggerRepository: CoTriggerRepository

    @Test
    fun `full insurance lifecycle from citizen registration to government report`() {
        val auth = adminAuth()

        // citizen registration -> SSA SSN validation
        // SSN 123456704: 9 digits, % 100 == 4 -> "California".
        val citizenResult = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/CitizenAR-api/save")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        CitizenRegistrationRequest(
                            fullName = "Jane Doe",
                            email = "jane.doe@example.com",
                            gender = "F",
                            phoneNo = 5551234567,
                            ssn = 123456704
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stateName").value("California"))
            .andReturn()

        val appId = objectMapper.readTree(citizenResult.response.contentAsString).get("appId").asLong()
        assertThat(citizenRepository.findByAppId(appId)).isNotNull

        // load case number
        val caseResult = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/dc-api/loadCaseNo/$appId")
                .header(HttpHeaders.AUTHORIZATION, auth)
        )
            .andExpect(status().isOk)
            .andReturn()
        val caseNo = objectMapper.readTree(caseResult.response.contentAsString).get("caseNo").asLong()

        // plan selection (SNAP)
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/dc-api/updatePlanSelection")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(PlanSelectionRequest(caseNo = caseNo, planId = planId("SNAP"))))
        ).andExpect(status().isOk)

        // income (empIncome < 300 -> SNAP approves)
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/dc-api/saveIncome")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(IncomeRequest(caseNo = caseNo, empIncome = 100.0, propertyIncome = 0.0)))
        ).andExpect(status().isOk)

        // eligibility (writes EligibilityDetails + PENDING CoTrigger)
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/ed-api/determine/$caseNo")
                .header(HttpHeaders.AUTHORIZATION, auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.planStatus").value("APPROVED"))
            .andExpect(jsonPath("$.benefitAmt").value(200.0))

        val eligibility = eligibilityRepository.findByCaseNo(caseNo)
        assertThat(eligibility).isNotNull
        assertThat(eligibility!!.planStatus).isEqualTo("APPROVED")
        assertThat(eligibility.holderName).isEqualTo("Jane Doe")

        val trigger = coTriggerRepository.findByCaseNo(caseNo)
        assertThat(trigger).isNotNull
        assertThat(trigger!!.triggerStatus).isEqualTo("PENDING")  // couples to correspondence

        // correspondence: consume PENDING trigger, email citizen, mark PROCESSED
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/co-triggers-api/process")
                .header(HttpHeaders.AUTHORIZATION, auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].caseNo").value(caseNo))
            .andExpect(jsonPath("$[0].triggerStatus").value("PROCESSED"))

        // one email to the citizen (mailSender mocked)
        val messageCaptor = ArgumentCaptor.forClass(MimeMessage::class.java)
        verify(mailSender, atLeastOnce()).send(messageCaptor.capture())
        val sent = messageCaptor.value
        assertThat(sent.allRecipients.map { it.toString() }).contains("jane.doe@example.com")
        assertThat(sent.subject).contains(caseNo.toString())

        val processedTrigger = coTriggerRepository.findByCaseNo(caseNo)
        assertThat(processedTrigger!!.triggerStatus).isEqualTo("PROCESSED")
        assertThat(processedTrigger.coNoticePdf).isNotNull  // PDF persisted

        // benefit batch: APPROVED rows -> CSV
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/bi-api/send")
                .header(HttpHeaders.AUTHORIZATION, auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        assertThat(BENEFIT_OUTPUT_FILE).exists()
        val csv = BENEFIT_OUTPUT_FILE.readText()
        assertThat(csv).contains("Case No,Holder Name,SSN")  // header
        assertThat(csv).contains(caseNo.toString())
        assertThat(csv).contains("ISH-Bank")                 // processor-assigned bank

        // govt report: 1 app, 1 approved, 0 denied
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/report-api/government/generate")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(ReportRequest(reportType = "MONTHLY", reportFormat = "TEXT")))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reportStatus").value("GENERATED"))

        // check aggregation via the downloaded report
        val reportId = objectMapper.readTree(
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/report-api/government")
                    .header(HttpHeaders.AUTHORIZATION, auth)
            ).andReturn().response.contentAsString
        ).get(0).get("reportId").asLong()

        val reportContent = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/report-api/government/download/$reportId")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andReturn().response.contentAsString

        assertThat(reportContent).contains("Total Applications: 1")
        assertThat(reportContent).contains("Approved: 1")
        assertThat(reportContent).contains("Denied: 0")
    }

    @Test
    fun `protected endpoint rejects request with no token`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/report-api/government")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `plan-api rejects non-admin role with 403`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/plan-api/all")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_USER"))
        ).andExpect(status().isForbidden)
    }
}
