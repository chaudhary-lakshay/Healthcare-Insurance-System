package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.PlanRule
import org.springframework.data.jpa.repository.JpaRepository

interface PlanRuleRepository : JpaRepository<PlanRule, Long> {
    fun findByPlanName(planName: String): PlanRule?
}
