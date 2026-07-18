package com.lakshay.healthcare

import com.lakshay.healthcare.casework.dto.RfiRequest
import com.lakshay.healthcare.casework.service.CaseworkService
import com.lakshay.healthcare.eligibility.service.EligibilityDeterminationService
import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.entity.DcIncome
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.DcIncomeRepository
import com.lakshay.healthcare.shared.repository.NoticeRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

// Trigger points for issue #12: determination + RFI both leave an EMAIL notice row
// (NotificationService writes one per send attempt), rendered from the Thymeleaf template.
class StatusEmailIT : IntegrationTestBase() {

    @Autowired lateinit var eligibilityService: EligibilityDeterminationService
    @Autowired lateinit var caseworkService: CaseworkService
    @Autowired lateinit var citizenRepo: CitizenAppRegistrationRepository
    @Autowired lateinit var dcCaseRepo: DcCaseRepository
    @Autowired lateinit var dcIncomeRepo: DcIncomeRepository
    @Autowired lateinit var noticeRepository: NoticeRepository

    private fun seedCase(empIncome: Double): Long {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = "Jane Doe", email = "jane@ish.test", gender = "F",
                ssn = 123456704L, stateName = "California"
            )
        )
        val case = dcCaseRepo.save(DcCase(appId = app.appId, planId = planId("SNAP")))
        dcIncomeRepo.save(DcIncome(caseNo = case.caseNo, empIncome = empIncome))
        return case.caseNo
    }

    private fun emailNotices(caseNo: Long, type: String) =
        noticeRepository.findAll().filter { it.caseNo == caseNo && it.channel == "EMAIL" && it.noticeType == type }

    @Test
    fun `approval sends a status email`() {
        val caseNo = seedCase(empIncome = 100.0)
        eligibilityService.determineEligibility(caseNo)

        val notices = emailNotices(caseNo, "STATUS_CHANGE")
        assertThat(notices).hasSize(1)
        assertThat(notices[0].status).isEqualTo("SENT")
        assertThat(notices[0].subject).contains("approved")
        assertThat(notices[0].body).contains("SNAP").contains("Jane Doe")
        assertThat(notices[0].body).doesNotContain("123456704") // no SSN, ever
    }

    @Test
    fun `denial sends a status email with the reason`() {
        val caseNo = seedCase(empIncome = 5000.0)
        eligibilityService.determineEligibility(caseNo)

        val notices = emailNotices(caseNo, "STATUS_CHANGE")
        assertThat(notices).hasSize(1)
        assertThat(notices[0].body).contains("denied").contains("High Income")
    }

    @Test
    fun `re-running an unchanged determination does not email again`() {
        val caseNo = seedCase(empIncome = 100.0)
        eligibilityService.determineEligibility(caseNo)
        eligibilityService.determineEligibility(caseNo)

        assertThat(emailNotices(caseNo, "STATUS_CHANGE")).hasSize(1)
    }

    @Test
    fun `rfi open sends an email with the message`() {
        val caseNo = seedCase(empIncome = 100.0)
        caseworkService.requestInfo(caseNo, RfiRequest(message = "Please upload proof of income"))

        val notices = emailNotices(caseNo, "RFI")
        assertThat(notices).hasSize(1)
        assertThat(notices[0].body).contains("Please upload proof of income")
    }
}
