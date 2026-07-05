package com.lakshay.healthcare

import com.lakshay.healthcare.report.dto.ReportRequest
import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.EligibilityDetails
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.shared.repository.GovernmentReportRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Government report ops under /report-api/government. generate snapshots the current
 * citizen/eligibility/plan counts into a text body and saves it; the read endpoints filter by
 * id/type/department/period; download streams it with a format-derived Content-Type; delete drops
 * it. ADMIN/WORKER route, bearer is ADMIN here. See docs/TESTING-FLOWS.md.
 */
class GovernmentReportIT : IntegrationTestBase() {

    @Autowired private lateinit var reportRepository: GovernmentReportRepository
    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository
    @Autowired private lateinit var eligibilityRepository: EligibilityDetailsRepository

    /** Seed [citizens] applications (= "total applications") plus [approved]/[denied] eligibility rows. */
    private fun seedStats(citizens: Int, approved: Int, denied: Int) {
        repeat(citizens) { i ->
            citizenRepo.save(
                CitizenAppRegistration(
                    fullName = "Citizen $i", email = "c$i@ish.test", gender = "F",
                    ssn = 100000000L + i, stateName = "California"
                )
            )
        }
        repeat(approved) { i ->
            eligibilityRepository.save(
                EligibilityDetails(caseNo = 1000L + i, planName = "SNAP", planStatus = "APPROVED", benefitAmt = 200.0)
            )
        }
        repeat(denied) { i ->
            eligibilityRepository.save(
                EligibilityDetails(caseNo = 2000L + i, planName = "SNAP", planStatus = "DENIED", denialReason = "High Income")
            )
        }
    }

    private fun generate(
        reportType: String = "MONTHLY",
        reportFormat: String = "TEXT",
        periodCovered: String? = "JUNE",
        departmentName: String? = "Health Dept"
    ): ResultActions = mockMvc.perform(
        post("/report-api/government/generate").header(HttpHeaders.AUTHORIZATION, adminAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(ReportRequest(reportType, reportFormat, periodCovered, departmentName = departmentName)))
    )

    private fun generatedId(reportType: String = "MONTHLY", reportFormat: String = "TEXT",
                            periodCovered: String? = "JUNE", departmentName: String? = "Health Dept"): Long {
        val body = generate(reportType, reportFormat, periodCovered, departmentName)
            .andExpect(status().isCreated).andReturn().response.contentAsString
        return objectMapper.readTree(body).get("reportId").asLong()
    }

    // generate

    @Test
    fun `REPORT-1 generate creates a GENERATED report`() {
        generate()
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reportId").isNumber)
            .andExpect(jsonPath("$.reportStatus").value("GENERATED"))
            .andExpect(jsonPath("$.reportType").value("MONTHLY"))
            .andExpect(jsonPath("$.departmentName").value("Health Dept"))
            .andExpect(jsonPath("$.downloadUrl").value(containsString("/report-api/government/download/")))

        assertThat(reportRepository.count()).isEqualTo(1)
    }

    @Test
    fun `REPORT-1 generate computes approval rate from counts`() {
        seedStats(citizens = 4, approved = 2, denied = 1) // rate = 2/4*100 = 50.0
        val id = generatedId()

        val body = mockMvc.perform(
            get("/report-api/government/download/$id").header(HttpHeaders.AUTHORIZATION, adminAuth())
        ).andReturn().response.contentAsString

        assertThat(body).contains("Total Applications: 4")
        assertThat(body).contains("Approved: 2")
        assertThat(body).contains("Denied: 1")
        assertThat(body).contains("Approval Rate: 50.0%")
    }

    @Test
    fun `REPORT-1 generate with no applications reports 0 percent`() {
        // No citizens seeded -> total = 0 -> the "%.1f" branch is skipped and a literal "0" is emitted.
        val id = generatedId()

        val body = mockMvc.perform(
            get("/report-api/government/download/$id").header(HttpHeaders.AUTHORIZATION, adminAuth())
        ).andReturn().response.contentAsString

        assertThat(body).contains("Total Applications: 0")
        assertThat(body).contains("Approval Rate: 0%")
    }

    // read endpoints

    @Test
    fun `REPORT-1 list returns all reports`() {
        generatedId(reportType = "MONTHLY")
        generatedId(reportType = "ANNUAL")

        mockMvc.perform(get("/report-api/government").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `REPORT-1 getById returns the report - unknown is 404`() {
        val id = generatedId()
        mockMvc.perform(get("/report-api/government/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reportId").value(id))

        mockMvc.perform(get("/report-api/government/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `REPORT-1 getByType filters by report type`() {
        generatedId(reportType = "MONTHLY")
        generatedId(reportType = "ANNUAL")

        mockMvc.perform(get("/report-api/government/type/MONTHLY").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].reportType").value("MONTHLY"))
    }

    @Test
    fun `REPORT-1 getByDepartment filters by department`() {
        generatedId(departmentName = "Health Dept")
        generatedId(departmentName = "Finance Dept")

        mockMvc.perform(
            get("/report-api/government/department").param("departmentName", "Finance Dept")
                .header(HttpHeaders.AUTHORIZATION, adminAuth())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].departmentName").value("Finance Dept"))
    }

    @Test
    fun `REPORT-1 getByPeriod filters by period`() {
        generatedId(periodCovered = "JUNE")
        generatedId(periodCovered = "JULY")

        mockMvc.perform(
            get("/report-api/government/period").param("periodCovered", "JULY")
                .header(HttpHeaders.AUTHORIZATION, adminAuth())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].periodCovered").value("JULY"))
    }

    // download: content-type by format

    @Test
    fun `REPORT-1 download sets content type from format`() {
        val pdfId = generatedId(reportFormat = "PDF")
        val excelId = generatedId(reportFormat = "EXCEL")
        val textId = generatedId(reportFormat = "TEXT")

        download(pdfId, "application/pdf")
        download(excelId, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        download(textId, "text/plain")
    }

    private fun download(id: Long, expectedContentType: String) {
        mockMvc.perform(get("/report-api/government/download/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, expectedContentType))
            .andExpect(content().string(containsString("GOVERNMENT REPORT")))
    }

    @Test
    fun `REPORT-1 download unknown report is 404`() {
        mockMvc.perform(get("/report-api/government/download/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }

    // delete

    @Test
    fun `REPORT-1 delete removes the report - unknown is 404`() {
        val id = generatedId()
        mockMvc.perform(delete("/report-api/government/$id").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNoContent)
        assertThat(reportRepository.existsById(id)).isFalse

        mockMvc.perform(delete("/report-api/government/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }
}
