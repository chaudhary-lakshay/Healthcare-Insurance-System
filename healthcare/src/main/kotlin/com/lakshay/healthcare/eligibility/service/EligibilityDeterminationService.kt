package com.lakshay.healthcare.eligibility.service

import com.lakshay.healthcare.eligibility.dto.EligibilityResponse
import com.lakshay.healthcare.eligibility.dto.ProgramResult
import com.lakshay.healthcare.eligibility.dto.ScreeningResponse
import com.lakshay.healthcare.shared.entity.CoTrigger
import com.lakshay.healthcare.shared.entity.EligibilityDetails
import com.lakshay.healthcare.shared.entity.*
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.audit.AuditService
import com.lakshay.healthcare.shared.lifecycle.CaseStateMachine
import com.lakshay.healthcare.shared.lifecycle.CaseStatus
import com.lakshay.healthcare.shared.repository.*
import com.lakshay.healthcare.shared.security.OwnershipService
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period

@Service
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
    private val ownershipService: OwnershipService
) {

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

        val output = applyPlanConditions(planName, income, education, children, citizenAge)

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
        auditService.record("CASE_DETERMINED", "DcCase", caseNo.toString(), "planStatus=${output.planStatus}")

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

        val programs = planRepository.findAll()
            .filter { it.activeSw == "Y" }
            .map { plan ->
                val r = applyPlanConditions(plan.planName, income, education, children, citizenAge)
                ProgramResult(plan.planName, r.planStatus, r.benefitAmt, r.denialReason)
            }
        auditService.record("CASE_SCREENED", "DcCase", caseNo.toString())
        return ScreeningResponse(caseNo, programs)
    }

    private fun applyPlanConditions(
        planName: String,
        income: DcIncome,
        education: DcEducation?,
        children: List<DcChildren>,
        citizenAge: Int
    ): EligibilityResponse {
        val empIncome = income.empIncome ?: 0.0
        val propertyIncome = income.propertyIncome ?: 0.0
        // Thresholds/amounts come from PLAN_RULE when configured, else the legacy literal (per field).
        val rule = planRuleRepository.findByPlanName(planName)

        return when (planName.uppercase()) {
            "SNAP" -> {
                if (empIncome < (rule?.incomeLimit ?: 300.0)) {
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
                    child.childDOB?.let { Period.between(it, LocalDate.now()).years <= 16 } ?: true
                }
                if (empIncome < (rule?.incomeLimit ?: 300.0) && hasEligibleKids && allKidsUnderLimit) {
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
                if (citizenAge >= 65) {
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
                if (empIncome < (rule?.incomeLimit ?: 300.0) && propertyIncome == 0.0) {
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
                if (empIncome == 0.0 && passOutYear != null && passOutYear <= LocalDate.now().year) {
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
                if (citizenAge >= 25) {
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
                if (totalIncome < 10000) {
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
