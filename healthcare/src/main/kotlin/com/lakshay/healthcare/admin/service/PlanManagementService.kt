package com.lakshay.healthcare.admin.service

import com.lakshay.healthcare.admin.dto.PlanCategoryResponse
import com.lakshay.healthcare.admin.dto.PlanDataResponse
import com.lakshay.healthcare.admin.dto.PlanRequest
import com.lakshay.healthcare.shared.entity.Plan
import com.lakshay.healthcare.shared.exception.DuplicateResourceException
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.repository.PlanCategoryRepository
import com.lakshay.healthcare.shared.repository.PlanRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PlanManagementService(
    private val planRepository: PlanRepository,
    private val categoryRepository: PlanCategoryRepository
) {

    fun registerPlan(request: PlanRequest): String {
        if (planRepository.findByPlanName(request.planName) != null) {
            throw DuplicateResourceException("Plan with name ${request.planName} already exists")
        }

        val plan = Plan(
            planName = request.planName,
            startDate = request.startDate?.let { LocalDate.parse(it) },
            endDate = request.endDate?.let { LocalDate.parse(it) },
            description = request.description,
            categoryId = request.categoryId,
            createdBy = "ADMIN",
            updatedBy = "ADMIN"
        )

        val saved = planRepository.save(plan)
        return "Plan registered successfully with ID: ${saved.planId}"
    }

    fun getCategories(): List<PlanCategoryResponse> {
        return categoryRepository.findAll().map {
            PlanCategoryResponse(categoryId = it.categoryId, categoryName = it.categoryName)
        }
    }

    fun showAllPlans(): List<PlanDataResponse> {
        return planRepository.findAll().map { toPlanData(it) }
    }

    fun showPlanById(planId: Long): PlanDataResponse {
        val plan = planRepository.findById(planId)
            .orElseThrow { ResourceNotFoundException("Plan not found: $planId") }
        return toPlanData(plan)
    }

    fun updatePlan(request: PlanDataResponse): String {
        val plan = planRepository.findById(request.planId)
            .orElseThrow { ResourceNotFoundException("Plan not found: ${request.planId}") }

        val updated = plan.copy(
            planName = request.planName,
            startDate = request.startDate?.let { LocalDate.parse(it) } ?: plan.startDate,
            endDate = request.endDate?.let { LocalDate.parse(it) } ?: plan.endDate,
            description = request.description ?: plan.description,
            categoryId = request.categoryId ?: plan.categoryId,
            updatedBy = "ADMIN",
            updationDate = LocalDate.now()
        )
        planRepository.save(updated)
        return "Plan ${request.planId} updated successfully"
    }

    fun deletePlan(planId: Long): String {
        val plan = planRepository.findById(planId)
            .orElseThrow { ResourceNotFoundException("Plan not found: $planId") }
        planRepository.delete(plan)
        return "Plan $planId deleted successfully"
    }

    fun changePlanStatus(planId: Long, status: String): String {
        val plan = planRepository.findById(planId)
            .orElseThrow { ResourceNotFoundException("Plan not found: $planId") }
        val updated = plan.copy(activeSw = status, updatedBy = "ADMIN", updationDate = LocalDate.now())
        planRepository.save(updated)
        return "Plan $planId status changed to $status"
    }

    private fun toPlanData(plan: Plan): PlanDataResponse {
        return PlanDataResponse(
            planId = plan.planId,
            planName = plan.planName,
            startDate = plan.startDate?.toString(),
            endDate = plan.endDate?.toString(),
            description = plan.description,
            categoryId = plan.categoryId,
            activeSw = plan.activeSw,
            createdBy = plan.createdBy
        )
    }
}
