package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*

// Tunable eligibility numbers per plan. Only the knobs that actually change (income limit, benefit
// amount) live here; the rule logic and the structural thresholds (ages, etc.) stay in code. Any
// field can be null - eligibility falls back to the legacy literal per field, so a partial row can
// never zero out a benefit.
@Entity
@Table(name = "PLAN_RULE")
data class PlanRule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    val ruleId: Long = 0,
    @Column(name = "plan_name")
    val planName: String,
    @Column(name = "income_limit")
    val incomeLimit: Double? = null,
    @Column(name = "benefit_amt")
    val benefitAmt: Double? = null
)
