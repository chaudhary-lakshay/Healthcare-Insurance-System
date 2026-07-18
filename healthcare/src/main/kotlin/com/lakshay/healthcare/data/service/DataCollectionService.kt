package com.lakshay.healthcare.data.service

import com.lakshay.healthcare.data.dto.CaseResponse
import com.lakshay.healthcare.data.dto.ChildrenRequest
import com.lakshay.healthcare.data.dto.DcSummaryResponse
import com.lakshay.healthcare.data.dto.EducationRequest
import com.lakshay.healthcare.data.dto.HouseholdMemberRequest
import com.lakshay.healthcare.data.dto.HouseholdMemberResponse
import com.lakshay.healthcare.data.dto.IncomeRequest
import com.lakshay.healthcare.data.dto.PlanNameResponse
import com.lakshay.healthcare.data.dto.PlanSelectionRequest
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.entity.DcChildren
import com.lakshay.healthcare.shared.entity.DcEducation
import com.lakshay.healthcare.shared.entity.DcIncome
import com.lakshay.healthcare.shared.entity.HouseholdMember
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.DcChildrenRepository
import com.lakshay.healthcare.shared.repository.DcEducationRepository
import com.lakshay.healthcare.shared.repository.DcIncomeRepository
import com.lakshay.healthcare.shared.repository.HouseholdMemberRepository
import com.lakshay.healthcare.shared.repository.PlanRepository
import com.lakshay.healthcare.shared.exception.DuplicateResourceException
import com.lakshay.healthcare.shared.exception.ValidationException
import com.lakshay.healthcare.shared.audit.AuditService
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
// LongParameterList: Spring constructor injection — each dependency is a distinct bean
@Suppress("LongParameterList")
class DataCollectionService(
    private val dcCaseRepository: DcCaseRepository,
    private val dcIncomeRepository: DcIncomeRepository,
    private val dcEducationRepository: DcEducationRepository,
    private val dcChildrenRepository: DcChildrenRepository,
    private val planRepository: PlanRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val householdMemberRepository: HouseholdMemberRepository,
    private val auditService: AuditService
) {

    fun loadCaseNo(appId: Long): CaseResponse {
        val citizen = citizenRepository.findByAppId(appId)
            ?: throw ResourceNotFoundException("Citizen application not found for ID: $appId")

        if (dcCaseRepository.findByAppId(appId) != null) {
            throw DuplicateResourceException("Case already exists for application ID: $appId")
        }

        val dcCase = DcCase(appId = appId)
        val saved = dcCaseRepository.save(dcCase)
        return CaseResponse(caseNo = saved.caseNo)
    }

    fun getPlanNames(): List<PlanNameResponse> {
        return planRepository.findAll().map {
            PlanNameResponse(planId = it.planId, planName = it.planName)
        }
    }

    fun updatePlanSelection(request: PlanSelectionRequest) {
        val dcCase = dcCaseRepository.findByCaseNo(request.caseNo)
            ?: throw ResourceNotFoundException("Case not found: ${request.caseNo}")

        val updated = dcCase.copy(planId = request.planId)
        dcCaseRepository.save(updated)
    }

    fun saveIncome(request: IncomeRequest): Long {
        val income = DcIncome(
            caseNo = request.caseNo,
            empIncome = request.empIncome,
            propertyIncome = request.propertyIncome
        )
        return dcIncomeRepository.save(income).incomeId
    }

    fun saveEducation(request: EducationRequest): Long {
        val education = DcEducation(
            caseNo = request.caseNo,
            highestQlfy = request.highestQlfy,
            passOutYear = request.passOutYear
        )
        return dcEducationRepository.save(education).educationId
    }

    fun saveChildren(request: List<ChildrenRequest>): List<Long> {
        return request.map { child ->
            val entity = DcChildren(
                caseNo = child.caseNo,
                childDOB = child.childDOB?.let { LocalDate.parse(it) },
                childSSN = child.childSSN
            )
            dcChildrenRepository.save(entity).childId
        }
    }

    // ThrowsCount: three distinct client errors (case-not-found 404, two 400 validations);
    // folding them would blur the messages/types or reorder the dob parse. Keep them explicit.
    @Suppress("ThrowsCount")
    fun saveHouseholdMember(request: HouseholdMemberRequest): Long {
        dcCaseRepository.findByCaseNo(request.caseNo)
            ?: throw ResourceNotFoundException("Case not found: ${request.caseNo}")
        if (request.relationship.isBlank()) throw ValidationException("relationship is required")
        val dob = request.dob?.let { LocalDate.parse(it) }
        if (dob != null && dob.isAfter(LocalDate.now())) throw ValidationException("dob cannot be in the future")
        val saved = householdMemberRepository.save(
            HouseholdMember(
                caseNo = request.caseNo,
                fullName = request.fullName,
                relationship = request.relationship,
                dob = dob,
                memberIncome = request.memberIncome
            )
        )
        auditService.record(
            "HOUSEHOLD_MEMBER_ADDED", "HouseholdMember", saved.memberId.toString(),
            "relationship=${request.relationship}"
        )
        return saved.memberId
    }

    fun getDcSummary(caseNo: Long): DcSummaryResponse {
        val dcCase = dcCaseRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Case not found: $caseNo")

        val income = dcIncomeRepository.findByCaseNo(caseNo)
        val education = dcEducationRepository.findByCaseNo(caseNo)
        val children = dcChildrenRepository.findByCaseNo(caseNo)
        val householdMembers = householdMemberRepository.findByCaseNo(caseNo)
        val citizen = citizenRepository.findByAppId(dcCase.appId)
        val planName = dcCase.planId?.let { planRepository.findById(it).orElse(null)?.planName }

        return DcSummaryResponse(
            caseNo = caseNo,
            planName = planName,
            citizenName = citizen?.fullName,
            citizenSsn = citizen?.ssn,
            income = income?.let {
                IncomeRequest(caseNo = caseNo, empIncome = it.empIncome, propertyIncome = it.propertyIncome)
            },
            education = education?.let {
                EducationRequest(caseNo = caseNo, highestQlfy = it.highestQlfy, passOutYear = it.passOutYear)
            },
            children = children.map {
                ChildrenRequest(caseNo = it.caseNo, childDOB = it.childDOB?.toString(), childSSN = it.childSSN)
            },
            householdMembers = householdMembers.map {
                HouseholdMemberResponse(
                    it.memberId, it.caseNo, it.fullName,
                    it.relationship, it.dob?.toString(), it.memberIncome
                )
            },
            householdSize = 1 + householdMembers.size
        )
    }
}
