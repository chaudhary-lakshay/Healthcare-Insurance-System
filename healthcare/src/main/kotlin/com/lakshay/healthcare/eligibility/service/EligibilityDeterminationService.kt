package com.lakshay.healthcare.eligibility.service

import com.lakshay.healthcare.eligibility.dto.EligibilityResponse
import com.lakshay.healthcare.shared.entity.CoTrigger
import com.lakshay.healthcare.shared.entity.EligibilityDetails
import com.lakshay.healthcare.shared.entity.*
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.lifecycle.CaseStateMachine
import com.lakshay.healthcare.shared.lifecycle.CaseStatus
import com.lakshay.healthcare.shared.repository.*
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
    private val caseStateMachine: CaseStateMachine
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

        val eligibilityEntity = EligibilityDetails(
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

        val coTrigger = CoTrigger(
            caseNo = caseNo,
            triggerStatus = "PENDING"
        )
        coTriggerRepository.save(coTrigger)

        // case went through eligibility -> mark DETERMINED (idempotent on re-run)
        dcCaseRepository.save(caseStateMachine.transition(dcCase, CaseStatus.DETERMINED))

        return output
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

        return when (planName.uppercase()) {
            "SNAP" -> {
                if (empIncome < 300) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = 200.0,
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
                if (empIncome < 300 && hasEligibleKids && allKidsUnderLimit) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = 300.0,
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
                        benefitAmt = 350.0,
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
                if (empIncome < 300 && propertyIncome == 0.0) {
                    EligibilityResponse(
                        planStatus = "APPROVED",
                        planName = planName,
                        benefitAmt = 200.0,
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
                        benefitAmt = 300.0,
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
