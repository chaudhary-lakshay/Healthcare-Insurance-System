package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.Plan
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlanRepository : JpaRepository<com.lakshay.healthcare.shared.entity.Plan, Long> {
    fun findByPlanName(planName: String): com.lakshay.healthcare.shared.entity.Plan?
    fun findByCategoryId(categoryId: Long): List<com.lakshay.healthcare.shared.entity.Plan>
}
