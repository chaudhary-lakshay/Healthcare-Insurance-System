package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "ELIGIBILITY_DETERMINATION")
data class EligibilityDetails(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ed_trace_id")
    val edTraceId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long,
    @Column(name = "holder_name")
    val holderName: String? = null,
    @Column(name = "holder_ssn")
    val holderSSN: Long? = null,
    @Column(name = "plan_name")
    val planName: String? = null,
    @Column(name = "plan_status")
    val planStatus: String? = null,
    @Column(name = "plan_start_date")
    val planStartDate: LocalDate? = null,
    @Column(name = "plan_end_date")
    val planEndDate: LocalDate? = null,
    @Column(name = "benefit_amt")
    val benefitAmt: Double? = null,
    @Column(name = "denial_reason")
    val denialReason: String? = null,
    @Column(name = "bank_name")
    val bankName: String? = null,
    @Column(name = "account_number")
    val accountNumber: String? = null
)
