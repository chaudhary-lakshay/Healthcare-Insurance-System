package com.lakshay.healthcare

import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.CoTrigger
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.entity.EligibilityDetails
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.CoTriggerRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import jakarta.mail.internet.MimeMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * Correspondence trigger processing (GET /co-triggers-api/process). For each PENDING CO_TRIGGER it
 * builds a benefit PDF, emails the citizen, stores the PDF, flips the trigger to PROCESSED. A trigger
 * with missing deps is caught and flipped to FAILED without killing the batch. authenticated()
 * endpoint, any bearer clears the filter. See docs/TESTING-FLOWS.md.
 */
class CorrespondenceIT : IntegrationTestBase() {

    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository
    @Autowired private lateinit var dcCaseRepo: DcCaseRepository
    @Autowired private lateinit var eligibilityRepository: EligibilityDetailsRepository
    @Autowired private lateinit var coTriggerRepository: CoTriggerRepository

    /** Seed citizen -> case -> approved eligibility, returning the case number. */
    private fun seedApprovedCase(
        ssn: Long = 123456704L,
        email: String = "citizen@ish.test"
    ): Long {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = "Jane Doe", email = email, gender = "F",
                ssn = ssn, stateName = "California", dob = LocalDate.of(1990, 1, 1)
            )
        )
        val case = dcCaseRepo.save(DcCase(appId = app.appId, planId = planId("SNAP")))
        eligibilityRepository.save(
            EligibilityDetails(
                caseNo = case.caseNo, holderName = "Jane Doe", holderSSN = ssn,
                planName = "SNAP", planStatus = "APPROVED", benefitAmt = 200.0
            )
        )
        return case.caseNo
    }

    private fun pendingTrigger(caseNo: Long): Long =
        coTriggerRepository.save(CoTrigger(caseNo = caseNo, triggerStatus = "PENDING")).triggerId

    private fun process() =
        mockMvc.perform(get("/co-triggers-api/process").header(HttpHeaders.AUTHORIZATION, adminAuth()))

    @Test
    fun `CORR-1 pending trigger is processed - pdf stored and email sent`() {
        val caseNo = seedApprovedCase()
        val triggerId = pendingTrigger(caseNo)

        process()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].triggerStatus").value("PROCESSED"))

        val saved = coTriggerRepository.findById(triggerId).get()
        assertThat(saved.triggerStatus).isEqualTo("PROCESSED")
        assertThat(saved.coNoticePdf).isNotNull
        assertThat(saved.coNoticePdf!!.size).isGreaterThan(0)
        verify(mailSender, times(1)).send(any(MimeMessage::class.java))
    }

    @Test
    fun `CORR-1 no pending triggers returns empty list and sends nothing`() {
        process()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        verify(mailSender, never()).send(any(MimeMessage::class.java))
    }

    @Test
    fun `CORR-1 trigger with missing eligibility is marked FAILED but batch continues`() {
        // Good trigger + a bad one (case number with no eligibility row). The bad one throws inside
        // processTrigger, is caught, flipped to FAILED, and the loop still processes the good one.
        val goodCase = seedApprovedCase(ssn = 123456704L, email = "good@ish.test")
        val goodId = pendingTrigger(goodCase)
        val badId = pendingTrigger(caseNo = 999_999L)

        process()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))

        assertThat(coTriggerRepository.findById(goodId).get().triggerStatus).isEqualTo("PROCESSED")
        assertThat(coTriggerRepository.findById(badId).get().triggerStatus).isEqualTo("FAILED")
        // The good email still went out despite the sibling failure.
        verify(mailSender, times(1)).send(any(MimeMessage::class.java))
    }

    @Test
    fun `CORR-1 mail failure marks the trigger FAILED`() {
        // A hard SMTP failure makes EmailUtils return false; processTrigger now surfaces that by
        // throwing, so the outer loop flips the trigger to FAILED instead of silently PROCESSING it.
        willThrow(RuntimeException("smtp down")).given(mailSender).send(any(MimeMessage::class.java))

        val caseNo = seedApprovedCase()
        val triggerId = pendingTrigger(caseNo)

        process()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].triggerStatus").value("FAILED"))

        val saved = coTriggerRepository.findById(triggerId).get()
        assertThat(saved.triggerStatus).isEqualTo("FAILED")
    }
}
