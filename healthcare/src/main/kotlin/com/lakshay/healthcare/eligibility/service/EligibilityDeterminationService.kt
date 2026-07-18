package com.lakshay.healthcare.eligibility.service

import com.lakshay.healthcare.eligibility.dto.EligibilityResponse
import com.lakshay.healthcare.eligibility.dto.ProgramResult
import com.lakshay.healthcare.eligibility.dto.ScreeningResponse
import com.lakshay.healthcare.shared.audit.AuditService
import com.lakshay.healthcare.shared.entity.CoTrigger
import com.lakshay.healthcare.shared.entity.DcChildren
import com.lakshay.healthcare.shared.entity.DcEducation
import com.lakshay.healthcare.shared.entity.DcIncome
import com.lakshay.healthcare.shared.entity.EligibilityDetails
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.lifecycle.CaseStateMachine
import com.lakshay.healthcare.shared.lifecycle.CaseStatus
import com.lakshay.healthcare.shared.notification.StatusEmailService
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.CoTriggerRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.DcChildrenRepository
import com.lakshay.healthcare.shared.repository.DcEducationRepository
import com.lakshay.healthcare.shared.repository.DcIncomeRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.shared.repository.HouseholdMemberRepository
import com.lakshay.healthcare.shared.repository.PlanRepository
import com.lakshay.healthcare.shared.repository.PlanRuleRepository
import com.lakshay.healthcare.shared.security.OwnershipService
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period

@Service
// LongParameterList: Spring constructor injection — each dependency is a distinct bean
@Suppress("LongParameterList")
class EligibilityDeterminationService(
    private val dcCaseRepository: DcCaseRepository,
    private val dcIncomeRepository: DcIncomeRepository,
    private val dcEducationRepository: DcEducationRepository,
    private val dcChildrenRepository: DcChildrenRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val planRepository: PlanRepository,
    private val eligibilityRepository: EligibilityDetailsRepository,
    private val coTriggerRepository: CoTriggerRepository,
    private val caseStateMachine: CaseStateMachine,
    private val auditService: AuditService,
    private val planRuleRepository: PlanRuleRepository,
    private val ownershipService: OwnershipService,
    private val householdMemberRepository: HouseholdMemberRepository,
    private val statusEmailService: StatusEmailService
) {

    companion object {
        private const val SNAP_DEFAULT_INCOME_LIMIT = 300.0
        private const val CCAP_MAX_CHILD_AGE = 16
        private const val CCAP_DEFAULT_INCOME_LIMIT = 300.0
        private const val MEDCARE_MIN_AGE = 65
        private const val MEDAID_DEFAULT_INCOME_LIMIT = 300.0
        private const val QHP_MIN_AGE = 25
        private const val FALLBACK_INCOME_LIMIT = 10000
    }

    // LongMethod/CyclomaticComplexMethod: load -> compute -> persist -> notify, reads top-to-bottom.
    // ThrowsCount: four dependent lookups (case, plan id, plan, income), each guards the next.
    // Covered end-to-end by EligibilityMatrixIT.
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount")
    fun determineEligibility(caseNo: Long): EligibilityResponse {
        val dcCase = dcCaseRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Case not found: $caseNo")

        val planId = dcCase.planId ?: throw ResourceNotFoundException("No plan selected for case: $caseNo")
        val appId = dcCase.appId

        val plan = planRepository.findById(planId).orElse(null)
            ?: throw ResourceNotFoundException("Plan not found: $planId")
        val planName = plan.planName

        val income = dcIncomeRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Income data not found for case: $caseNo")

        val education = dcEducationRepository.findByCaseNo(caseNo)
        val children = dcChildrenRepository.findByCaseNo(caseNo)
        val citizen = citizenRepository.findByAppId(appId)

        val citizenAge = citizen?.dob?.let { Period.between(it, LocalDate.now()).years } ?: 0
        val citizenName = citizen?.fullName ?: "Unknown"
        val citizenSSN = citizen?.ssn

        val householdIncome = householdMemberRepository.findByCaseNo(caseNo).sumOf { it.memberIncome ?: 0.0 }
        val output = applyPlanConditions(planName, income, education, children, citizenAge, householdIncome)

        // Idempotent: re-running determination updates the case's existing rows instead of
        // duplicating them. The copy keeps ed_trace_id and the batch-written bank/account fields.
        val existing = eligibilityRepository.findByCaseNo(caseNo)
        val eligibilityEntity = existing?.copy(
            holderName = citizenName,
            holderSSN = citizenSSN,
            planName = planName,
            planStatus = output.planStatus,
            planStartDate = output.planStartDate,
            planEndDate = output.planEndDate,
            benefitAmt = output.benefitAmt,
            denialReason = output.denialReason
        ) ?: EligibilityDetails(
            caseNo = caseNo,
            holderName = citizenName,
            holderSSN = citizenSSN,
            planName = planName,
            planStatus = output.planStatus,
            planStartDate = output.planStartDate,
            planEndDate = output.planEndDate,
            benefitAmt = output.benefitAmt,
            denialReason = output.denialReason
        )
        eligibilityRepository.save(eligibilityEntity)

        // Re-flag the case's trigger PENDING so correspondence re-notifies; don't pile up triggers.
        val existingTrigger = coTriggerRepository.findByCaseNo(caseNo)
        val coTrigger = existingTrigger?.copy(triggerStatus = "PENDING")
            ?: CoTrigger(caseNo = caseNo, triggerStatus = "PENDING")
        coTriggerRepository.save(coTrigger)

        // case went through eligibility -> mark DETERMINED (idempotent on re-run)
        dcCaseRepository.save(caseStateMachine.transition(dcCase, CaseStatus.DETERMINED))
        auditService.record(
            "CASE_DETERMINED",
            "DcCase",
            caseNo.toString(),
            "planStatus=${output.planStatus}; " +
                "applicantIncome=${income.empIncome ?: 0.0}; householdIncome=$householdIncome"
        )

        // only on an actual status flip — re-runs are idempotent and shouldn't spam
        if (existing?.planStatus != output.planStatus) {
            citizen?.email?.let { email ->
                statusEmailService.caseStatusChanged(
                    caseNo = caseNo,
                    recipient = email,
                    citizenName = citizenName,
                    status = output.planStatus ?: "UNKNOWN",
                    planName = planName,
                    benefitAmt = output.benefitAmt,
                    denialReason = output.denialReason
                )
            }
        }

        return output
    }

    // Advisory multi-program screening: which active plans this applicant would qualify for given the
    // data already collected. Read-only - never persists or changes the case's official determination.
    // Ownership-checked (a citizen screens only their own case). Requires income data, same precondition
    // as determineEligibility -> 404 if absent.
    fun screen(caseNo: Long): ScreeningResponse {
        ownershipService.assertCanAccessCase(caseNo)
        val dcCase = dcCaseRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Case not found: $caseNo")
        val income = dcIncomeRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Income data not found for case: $caseNo")
        val education = dcEducationRepository.findByCaseNo(caseNo)
        val children = dcChildrenRepository.findByCaseNo(caseNo)
        val citizen = citizenRepository.findByAppId(dcCase.appId)
        val citizenAge = citizen?.dob?.let { Period.between(it, LocalDate.now()).years } ?: 0
        val householdIncome = householdMemberRepository.findByCaseNo(caseNo).sumOf { it.memberIncome ?: 0.0 }

        val programs = planRepository.findAll()
            .filter { it.activeSw == "Y" }
            .map { plan ->
                val r = applyPlanConditions(plan.planName, income, education, children, citizenAge, householdIncome)
                ProgramResult(plan.planName, r.planStatus, r.benefitAmt, r.denialReason)
            }
        auditService.record("CASE_SCREENED", "DcCase", caseNo.toString())
        return ScreeningResponse(caseNo, programs)
    }

    // LongMethod/CyclomaticComplexMethod: this IS the benefit decision table — one branch per
    // program (SNAP/CCAP/MEDCARE/MEDAID/CAJW/QHP/fallback). Splitting it scatters the rules.
    // LongParameterList: the six inputs are the determination context, passed straight through.
    @Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
    private fun applyPlanConditions(
        planName: String,
        income: DcIncome,
        education: DcEducation?,
        children: List<DcChildren>,
        citizenAge: Int,
        householdIncome: Double
    ): EligibilityResponse {
        val applicantIncome = income.empIncome ?: 0.0
        // Income-limit rules count the whole household; CAJW (below) uses applicant income only.
        val empIncome = applicantIncome + householdIncome
        val propertyIncome = income.propertyIncome ?: 0.0
        // Thresholds/amounts come from PLAN_RULE when configured, else the legacy literal (per field).
        val rule = planRuleRepository.findByPlanName(planName)

        return when (planName.uppercase()) {
            "SNAP" -> {
                if (empIncome < (rule?.incomeLimit ?: SNAP_DEFAULT_INCOME_LIMIT)) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = rule?.benefitAmt ?: 200.0,
                        denialReason = null,
                        planStartDate = LocalDate.now(),
                        planEndDate = LocalDate.now().plusYears(2)
                    )
                } else {
                    EligibilityResponse(
                        planStatus = "DENIED",
                        planName = planName,
                        benefitAmt = null,
                        denialReason = "High Income"
                    )
                }
            }
            "CCAP" -> {
                val hasEligibleKids = children.isNotEmpty()
                val allKidsUnderLimit = children.all { child ->
                    child.childDOB?.let { Period.between(it, LocalDate.now()).years <= CCAP_MAX_CHILD_AGE } ?: true
                }
                val incomeUnderLimit = empIncome < (rule?.incomeLimit ?: CCAP_DEFAULT_INCOME_LIMIT)
                if (incomeUnderLimit && hasEligibleKids && allKidsUnderLimit) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = rule?.benefitAmt ?: 300.0,
                        denialReason = null,
                        planStartDate = LocalDate.now(),
                        planEndDate = LocalDate.now().plusYears(2)
                    )
                } else {
                    EligibilityResponse(
                        planStatus = "DENIED",
                        planName = planName,
                        benefitAmt = null,
                        denialReason = "CCAP rules are not satisfied"
                    )
                }
            }
            "MEDCARE" -> {
                if (citizenAge >= MEDCARE_MIN_AGE) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = rule?.benefitAmt ?: 350.0,
                        denialReason = null,
                        planStartDate = LocalDate.now(),
                        planEndDate = LocalDate.now().plusYears(2)
                    )
                } else {
                    EligibilityResponse(
                        planStatus = "DENIED",
                        planName = planName,
                        benefitAmt = null,
                        denialReason = "MEDCARE rules are not satisfied"
                    )
                }
            }
            "MEDAID" -> {
                if (empIncome < (rule?.incomeLimit ?: MEDAID_DEFAULT_INCOME_LIMIT) && propertyIncome == 0.0) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = rule?.benefitAmt ?: 200.0,
                        denialReason = null,
                        planStartDate = LocalDate.now(),
                        planEndDate = LocalDate.now().plusYears(2)
                    )
                } else {
                    EligibilityResponse(
                        planStatus = "DENIED",
                        planName = planName,
                        benefitAmt = null,
                        denialReason = "MEDAID rules are not satisfied"
                    )
                }
            }
            "CAJW" -> {
                val passOutYear = education?.passOutYear
                if (applicantIncome == 0.0 && passOutYear != null && passOutYear <= LocalDate.now().year) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = rule?.benefitAmt ?: 300.0,
                        denialReason = null,
                        planStartDate = LocalDate.now(),
                        planEndDate = LocalDate.now().plusYears(2)
                    )
                } else {
                    EligibilityResponse(
                        planStatus = "DENIED",
                        planName = planName,
                        benefitAmt = null,
                        denialReason = "CAJW rules are not satisfied"
                    )
                }
            }
            "QHP" -> {
                if (citizenAge >= QHP_MIN_AGE) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = null,
                        denialReason = null,
                        planStartDate = LocalDate.now(),
                        planEndDate = LocalDate.now().plusYears(2)
                    )
                } else {
                    EligibilityResponse(
                        planStatus = "DENIED",
                        planName = planName,
                        benefitAmt = null,
                        denialReason = "QHP rules are not satisfied"
                    )
                }
            }
            else -> {
                val totalIncome = empIncome + propertyIncome
                if (totalIncome < FALLBACK_INCOME_LIMIT) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = totalIncome * 0.1,
                        denialReason = null,
                        planStartDate = LocalDate.now(),
                        planEndDate = LocalDate.now().plusYears(2)
                    )
                } else {
                    EligibilityResponse(
                        planStatus = "DENIED",
                        planName = planName,
                        benefitAmt = null,
                        denialReason = "Eligibility rules not satisfied for $planName"
                    )
                }
            }
        }
    }
}
