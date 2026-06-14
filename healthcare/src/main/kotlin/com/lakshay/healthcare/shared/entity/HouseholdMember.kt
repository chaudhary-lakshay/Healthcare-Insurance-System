package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

// A non-applicant person in a case's household (spouse, parent, other adult, etc.).
// No SSN here on purpose — no rule needs it yet (PII minimization). member_income is this person's
// monthly employment income, same unit as DcIncome.empIncome.
@Entity
@Table(name = "HOUSEHOLD_MEMBER")
data class HouseholdMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    val memberId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long,
    @Column(name = "full_name")
    val fullName: String? = null,
    @Column(name = "relationship")
    val relationship: String,
    @Column(name = "dob")
    val dob: LocalDate? = null,
    @Column(name = "member_income")
    val memberIncome: Double? = null,
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
