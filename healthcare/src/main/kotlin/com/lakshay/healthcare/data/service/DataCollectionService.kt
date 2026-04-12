package com.lakshay.healthcare.data.service

import com.lakshay.healthcare.data.dto.*
import com.lakshay.healthcare.shared.entity.*
import com.lakshay.healthcare.shared.repository.*
import com.lakshay.healthcare.shared.exception.DuplicateResourceException
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DataCollectionService(
    private val dcCaseRepository: DcCaseRepository,
    private val dcIncomeRepository: DcIncomeRepository,
    private val dcEducationRepository: DcEducationRepository,
    private val dcChildrenRepository: DcChildrenRepository,
    private val planRepository: PlanRepository,
    private val citizenRepository: CitizenAppRegistrationRepository
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

    fun getDcSummary(caseNo: Long): DcSummaryResponse {
        val dcCase = dcCaseRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Case not found: $caseNo")

        val income = dcIncomeRepository.findByCaseNo(caseNo)
        val education = dcEducationRepository.findByCaseNo(caseNo)
        val children = dcChildrenRepository.findByCaseNo(caseNo)
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
            }
        )
    }
}
