package com.lakshay.healthcare

import com.lakshay.healthcare.eligibility.service.EligibilityDeterminationService
import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.entity.DcChildren
import com.lakshay.healthcare.shared.entity.DcEducation
import com.lakshay.healthcare.shared.entity.DcIncome
import com.lakshay.healthcare.shared.entity.Plan
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.CoTriggerRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.DcChildrenRepository
import com.lakshay.healthcare.shared.repository.DcEducationRepository
import com.lakshay.healthcare.shared.repository.DcIncomeRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

/**
 * Hits the eligibility rule engine straight at the service layer — the gnarliest logic in the app.
 * One parameterized row per plan branch (approve/deny/boundary), plus the rows a determination
 * commits and the not-found paths. Service-layer not MockMvc: the rules are pure domain logic, HTTP
 * would just add JWT/serialization noise. Data seeded through repos and committed for real.
 *
 * Rule table (service is the source of truth):
 *   SNAP    empIncome < 300                                   -> APPROVED $200
 *   CCAP    empIncome < 300 && kids nonEmpty && all kids <=16 -> APPROVED $300
 *   MEDCARE age >= 65                                         -> APPROVED $350
 *   MEDAID  empIncome < 300 && propertyIncome == 0            -> APPROVED $200
 *   CAJW    empIncome == 0 && passOutYear <= thisYear         -> APPROVED $300
 *   QHP     age >= 25                                         -> APPROVED (no benefit amount)
 *   <other> empIncome+propertyIncome < 10000                 -> APPROVED 10% of total
 */
class EligibilityMatrixIT : IntegrationTestBase() {

    @Autowired private lateinit var eligibilityService: EligibilityDeterminationService

    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository

    @Autowired private lateinit var dcCaseRepo: DcCaseRepository

    @Autowired private lateinit var dcIncomeRepo: DcIncomeRepository

    @Autowired private lateinit var dcEducationRepo: DcEducationRepository

    @Autowired private lateinit var dcChildrenRepo: DcChildrenRepository

    @Autowired private lateinit var eligibilityRepository: EligibilityDetailsRepository

    @Autowired private lateinit var coTriggerRepository: CoTriggerRepository

    @Autowired private lateinit var planRuleRepository: com.lakshay.healthcare.shared.repository.PlanRuleRepository

    @Autowired
    private lateinit var householdMemberRepository: com.lakshay.healthcare.shared.repository.HouseholdMemberRepository

    /** The else-branch of the rule engine needs a plan whose name is not one of the six keywords. */
    @BeforeEach
    fun seedFallbackPlan() {
        val categoryId = planRepository.findByPlanName("SNAP")!!.categoryId
        planRepository.save(Plan(planName = FALLBACK_PLAN, categoryId = categoryId))
    }

    /**
     * Build a complete case the rule engine can read: citizen application -> case -> income, plus
     * optional education and children. [age] sets the citizen DOB; null means no DOB (age resolves
     * to 0). Returns the generated case number.
     */
    @Suppress("LongParameterList") // test data builder — optional case knobs via named default args
    private fun seedCase(
        planName: String,
        empIncome: Double? = null,
        propertyIncome: Double? = null,
        age: Int? = null,
        passOutYear: Int? = null,
        childAges: List<Int> = emptyList(),
        ssn: Long = 123456704L,
        fullName: String = "Jane Doe"
    ): Long {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = fullName,
                email = "citizen@ish.test",
                gender = "F",
                ssn = ssn,
                stateName = "California",
                dob = age?.let { LocalDate.now().minusYears(it.toLong()) }
            )
        )
        val case = dcCaseRepo.save(DcCase(appId = app.appId, planId = planId(planName)))
        dcIncomeRepo.save(
            DcIncome(caseNo = case.caseNo, empIncome = empIncome, propertyIncome = propertyIncome)
        )
        passOutYear?.let { dcEducationRepo.save(DcEducation(caseNo = case.caseNo, passOutYear = it)) }
        childAges.forEach { childAge ->
            dcChildrenRepo.save(
                DcChildren(caseNo = case.caseNo, childDOB = LocalDate.now().minusYears(childAge.toLong()))
            )
        }
        return case.caseNo
    }

    // the matrix: one row per branch

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrixRows")
    fun `ELIG-MATRIX rule branch`(row: Row) {
        val caseNo = seedCase(
            planName = row.plan,
            empIncome = row.empIncome,
            propertyIncome = row.propertyIncome,
            age = row.age,
            passOutYear = row.passOutYear,
            childAges = row.childAges
        )

        val result = eligibilityService.determineEligibility(caseNo)

        assertThat(result.planStatus).isEqualTo(row.expectedStatus)
        assertThat(result.benefitAmt).isEqualTo(row.expectedBenefit)
        assertThat(result.denialReason).isEqualTo(row.expectedDenial)
        if (row.expectedStatus == "APPROVED") {
            assertThat(result.planStartDate).isEqualTo(LocalDate.now())
            assertThat(result.planEndDate).isEqualTo(LocalDate.now().plusYears(2))
        }
    }

    // side effects every determination commits

    @Test
    fun `ELIG-MATRIX persists determination row and pending co-trigger`() {
        val caseNo = seedCase(planName = "SNAP", empIncome = 100.0)

        eligibilityService.determineEligibility(caseNo)

        val saved = eligibilityRepository.findByCaseNo(caseNo)!!
        assertThat(saved.planName).isEqualTo("SNAP")
        assertThat(saved.planStatus).isEqualTo("APPROVED")
        assertThat(saved.benefitAmt).isEqualTo(200.0)
        assertThat(saved.holderName).isEqualTo("Jane Doe")
        assertThat(saved.holderSSN).isEqualTo(123456704L)

        val trigger = coTriggerRepository.findByCaseNo(caseNo)!!
        assertThat(trigger.triggerStatus).isEqualTo("PENDING")
    }

    @Test
    fun `ELIG-MATRIX denied case still records the row and a co-trigger`() {
        val caseNo = seedCase(planName = "SNAP", empIncome = 5000.0)

        eligibilityService.determineEligibility(caseNo)

        val saved = eligibilityRepository.findByCaseNo(caseNo)!!
        assertThat(saved.planStatus).isEqualTo("DENIED")
        assertThat(saved.benefitAmt).isNull()
        assertThat(saved.denialReason).isEqualTo("High Income")
        assertThat(coTriggerRepository.findByCaseNo(caseNo)).isNotNull
    }

    // not-found paths

    @Test
    fun `ELIG-MATRIX unknown case number throws`() {
        val ex = assertThrows(ResourceNotFoundException::class.java) {
            eligibilityService.determineEligibility(999_999L)
        }
        assertThat(ex.message).contains("Case not found")
    }

    @Test
    fun `ELIG-MATRIX case with no plan selected throws`() {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = "No Plan",
                email = "noplan@ish.test",
                gender = "M",
                ssn = 111111111L,
                stateName = "California"
            )
        )
        val caseNo = dcCaseRepo.save(DcCase(appId = app.appId, planId = null)).caseNo

        val ex = assertThrows(ResourceNotFoundException::class.java) {
            eligibilityService.determineEligibility(caseNo)
        }
        assertThat(ex.message).contains("No plan selected")
    }

    @Test
    fun `ELIG-MATRIX case referencing a missing plan throws`() {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = "Bad Plan",
                email = "badplan@ish.test",
                gender = "M",
                ssn = 222222222L,
                stateName = "California"
            )
        )
        val caseNo = dcCaseRepo.save(DcCase(appId = app.appId, planId = 888_888L)).caseNo

        val ex = assertThrows(ResourceNotFoundException::class.java) {
            eligibilityService.determineEligibility(caseNo)
        }
        assertThat(ex.message).contains("Plan not found")
    }

    @Test
    fun `ELIG-MATRIX case with no income data throws`() {
        val app = citizenRepo.save(
            CitizenAppRegistration(
                fullName = "No Income",
                email = "noincome@ish.test",
                gender = "F",
                ssn = 333333333L,
                stateName = "California"
            )
        )
        val caseNo = dcCaseRepo.save(DcCase(appId = app.appId, planId = planId("SNAP"))).caseNo

        val ex = assertThrows(ResourceNotFoundException::class.java) {
            eligibilityService.determineEligibility(caseNo)
        }
        assertThat(ex.message).contains("Income data not found")
    }

    @Test
    fun `ELIG-MATRIX falls back to the legacy amount when the configured benefit is null`() {
        val snapRule = planRuleRepository.findByPlanName("SNAP")!!
        planRuleRepository.save(snapRule.copy(benefitAmt = null))
        try {
            val caseNo = seedCase(planName = "SNAP", empIncome = 100.0)
            val result = eligibilityService.determineEligibility(caseNo)
            assertThat(result.benefitAmt).isEqualTo(200.0)
        } finally {
            planRuleRepository.save(snapRule)
        }
    }

    @Test
    fun `ELIG-MATRIX re-determination updates in place without duplicate rows`() {
        val caseNo = seedCase(planName = "SNAP", empIncome = 100.0)
        eligibilityService.determineEligibility(caseNo)
        eligibilityService.determineEligibility(caseNo)
        assertThat(eligibilityRepository.findAll().count { it.caseNo == caseNo }).isEqualTo(1)
        assertThat(coTriggerRepository.findAll().count { it.caseNo == caseNo }).isEqualTo(1)
    }

    @Test
    fun `ELIG-MATRIX household member income counts toward the income limit`() {
        // Applicant alone (250 < 300) would be approved for SNAP; a household member's income pushes
        // the household total to 350, over the limit -> denied.
        val caseNo = seedCase(planName = "SNAP", empIncome = 250.0)
        householdMemberRepository.save(
            com.lakshay.healthcare.shared.entity.HouseholdMember(
                caseNo = caseNo,
                relationship = "SPOUSE",
                memberIncome = 100.0
            )
        )
        val result = eligibilityService.determineEligibility(caseNo)
        assertThat(result.planStatus).isEqualTo("DENIED")
        assertThat(result.denialReason).isEqualTo("High Income")
    }

    /** One parameterized matrix case. */
    data class Row(
        val name: String,
        val plan: String,
        val empIncome: Double? = null,
        val propertyIncome: Double? = null,
        val age: Int? = null,
        val passOutYear: Int? = null,
        val childAges: List<Int> = emptyList(),
        val expectedStatus: String,
        val expectedBenefit: Double? = null,
        val expectedDenial: String? = null
    ) {
        override fun toString() = name
    }

    companion object {
        const val FALLBACK_PLAN = "OTHER"

        // LongMethod: test data table — one Row per rule branch, one-arg-per-line after ktlint wrapping.
        @Suppress("LongMethod")
        @JvmStatic
        fun matrixRows(): List<Arguments> = listOf(
            // SNAP: empIncome < 300
            Row(
                "SNAP approved (income 100)",
                "SNAP",
                empIncome = 100.0,
                expectedStatus = "APPROVED",
                expectedBenefit = 200.0
            ),
            Row(
                "SNAP denied (income 500)",
                "SNAP",
                empIncome = 500.0,
                expectedStatus = "DENIED",
                expectedDenial = "High Income"
            ),
            Row(
                "SNAP boundary income==300 is denied",
                "SNAP",
                empIncome = 300.0,
                expectedStatus = "DENIED",
                expectedDenial = "High Income"
            ),

            // CCAP: income < 300 && has kids && all kids age <= 16
            Row(
                "CCAP approved (income 100, one kid age 5)",
                "CCAP",
                empIncome = 100.0,
                childAges = listOf(5),
                expectedStatus = "APPROVED",
                expectedBenefit = 300.0
            ),
            Row(
                "CCAP denied (no children)",
                "CCAP",
                empIncome = 100.0,
                expectedStatus = "DENIED",
                expectedDenial = "CCAP rules are not satisfied"
            ),
            Row(
                "CCAP denied (a kid over 16)",
                "CCAP",
                empIncome = 100.0,
                childAges = listOf(17),
                expectedStatus = "DENIED",
                expectedDenial = "CCAP rules are not satisfied"
            ),
            Row(
                "CCAP boundary kid age==16 approved",
                "CCAP",
                empIncome = 100.0,
                childAges = listOf(16),
                expectedStatus = "APPROVED",
                expectedBenefit = 300.0
            ),

            // MEDCARE: age >= 65
            Row(
                "MEDCARE approved (age 70)",
                "MEDCARE",
                age = 70,
                expectedStatus = "APPROVED",
                expectedBenefit = 350.0
            ),
            Row(
                "MEDCARE denied (age 40)",
                "MEDCARE",
                age = 40,
                expectedStatus = "DENIED",
                expectedDenial = "MEDCARE rules are not satisfied"
            ),
            Row(
                "MEDCARE boundary age==65 approved",
                "MEDCARE",
                age = 65,
                expectedStatus = "APPROVED",
                expectedBenefit = 350.0
            ),

            // MEDAID: income < 300 && propertyIncome == 0
            Row(
                "MEDAID approved (income 100, no property)",
                "MEDAID",
                empIncome = 100.0,
                propertyIncome = 0.0,
                expectedStatus = "APPROVED",
                expectedBenefit = 200.0
            ),
            Row(
                "MEDAID denied (has property income)",
                "MEDAID",
                empIncome = 100.0,
                propertyIncome = 50.0,
                expectedStatus = "DENIED",
                expectedDenial = "MEDAID rules are not satisfied"
            ),

            // CAJW: empIncome == 0 && passOutYear <= thisYear
            Row(
                "CAJW approved (no income, passed out)",
                "CAJW",
                empIncome = 0.0,
                passOutYear = 2020,
                expectedStatus = "APPROVED",
                expectedBenefit = 300.0
            ),
            Row(
                "CAJW denied (has income)",
                "CAJW",
                empIncome = 10.0,
                passOutYear = 2020,
                expectedStatus = "DENIED",
                expectedDenial = "CAJW rules are not satisfied"
            ),
            Row(
                "CAJW denied (no education record)",
                "CAJW",
                empIncome = 0.0,
                expectedStatus = "DENIED",
                expectedDenial = "CAJW rules are not satisfied"
            ),

            // QHP: age >= 25, approval carries no benefit amount
            Row(
                "QHP approved (age 30, no benefit amount)",
                "QHP",
                age = 30,
                expectedStatus = "APPROVED",
                expectedBenefit = null
            ),
            Row(
                "QHP denied (age 20)",
                "QHP",
                age = 20,
                expectedStatus = "DENIED",
                expectedDenial = "QHP rules are not satisfied"
            ),
            Row(
                "QHP boundary age==25 approved",
                "QHP",
                age = 25,
                expectedStatus = "APPROVED",
                expectedBenefit = null
            ),

            // Fallback else-branch: total income < 10000 -> 10% benefit
            Row(
                "OTHER approved (income 5000 -> 10% = 500)",
                FALLBACK_PLAN,
                empIncome = 5000.0,
                expectedStatus = "APPROVED",
                expectedBenefit = 500.0
            ),
            Row(
                "OTHER denied (income 20000)",
                FALLBACK_PLAN,
                empIncome = 20000.0,
                expectedStatus = "DENIED",
                expectedDenial = "Eligibility rules not satisfied for OTHER"
            )
        ).map { Arguments.of(it) }
    }
}
