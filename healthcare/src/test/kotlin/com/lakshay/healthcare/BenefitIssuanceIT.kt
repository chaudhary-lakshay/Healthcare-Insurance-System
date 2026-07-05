package com.lakshay.healthcare

import com.lakshay.healthcare.shared.entity.EligibilityDetails
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Spring Batch benefit issuance (GET /bi-api/send). Reads every ELIGIBILITY_DETERMINATION row with
 * planStatus = 'APPROVED', the processor stamps a bank + account number, the writer appends one CSV
 * line per row under a fixed header. Test profile points the writer at BENEFIT_OUTPUT_FILE.
 *
 * Watch out: the stamped values go to the CSV only, never back to the DB — the source row keeps
 * null bank/account. See docs/TESTING-FLOWS.md.
 */
class BenefitIssuanceIT : IntegrationTestBase() {

    @Autowired private lateinit var eligibilityRepository: EligibilityDetailsRepository

    private val csvHeader = "Case No,Holder Name,SSN,Plan Name,Benefit Amount,Bank Name,Account Number"

    private fun approved(caseNo: Long, ssn: Long = 123456704L): EligibilityDetails =
        eligibilityRepository.save(
            EligibilityDetails(
                caseNo = caseNo, holderName = "Jane Doe", holderSSN = ssn,
                planName = "SNAP", planStatus = "APPROVED", benefitAmt = 200.0
            )
        )

    private fun denied(caseNo: Long): EligibilityDetails =
        eligibilityRepository.save(
            EligibilityDetails(
                caseNo = caseNo, holderName = "Bob Roe", planName = "SNAP",
                planStatus = "DENIED", denialReason = "High Income"
            )
        )

    private fun runJob() =
        mockMvc.perform(get("/bi-api/send").header(HttpHeaders.AUTHORIZATION, adminAuth()))

    @Test
    fun `BENEFIT-1 approved row produces a CSV line and job completes`() {
        approved(caseNo = 101L)

        runJob()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.jobId").isNumber)

        val lines = BENEFIT_OUTPUT_FILE.readLines()
        assertThat(lines.first()).isEqualTo(csvHeader)
        assertThat(lines).hasSize(2)
        assertThat(lines[1]).startsWith("101,Jane Doe,123456704,SNAP,200.0,ISH-Bank,ISH101")
    }

    @Test
    fun `BENEFIT-1 no approved rows writes header only`() {
        denied(caseNo = 201L) // present but excluded by the APPROVED filter

        runJob()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        val lines = BENEFIT_OUTPUT_FILE.readLines()
        assertThat(lines).containsExactly(csvHeader)
    }

    @Test
    fun `BENEFIT-1 only approved rows are written - denied excluded`() {
        approved(caseNo = 301L)
        approved(caseNo = 302L)
        denied(caseNo = 303L)

        runJob().andExpect(status().isOk)

        val lines = BENEFIT_OUTPUT_FILE.readLines()
        assertThat(lines).hasSize(3) // header + 2 approved
        assertThat(lines.none { it.startsWith("303,") }).isTrue
    }

    @Test
    fun `BENEFIT-1 reads across page boundary - 12 approved rows all written`() {
        // Reader page size is 10; 12 rows forces a second page. All must still be written.
        (1L..12L).forEach { approved(caseNo = 400L + it, ssn = 100000000L + it) }

        runJob().andExpect(status().isOk)

        val lines = BENEFIT_OUTPUT_FILE.readLines()
        assertThat(lines).hasSize(13) // header + 12
    }

    @Test
    fun `BENEFIT-1 processor result is not written back to the DB`() {
        val caseNo = 501L
        approved(caseNo = caseNo)

        runJob().andExpect(status().isOk)

        // CSV got the stamped bank/account, but the source row stays null (processor returns a copy).
        val row = eligibilityRepository.findByCaseNo(caseNo)!!
        assertThat(row.bankName).isNull()
        assertThat(row.accountNumber).isNull()
    }
}
