package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.envers.Audited
import org.hibernate.envers.NotAudited
import java.time.LocalDate

// Audited, but SSN and bank details are @NotAudited — an immutable _AUD copy of those
// would be impossible to erase later.
@Audited
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
    @NotAudited
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
    @NotAudited
    @Column(name = "bank_name")
    val bankName: String? = null,
    @NotAudited
    @Column(name = "account_number")
    val accountNumber: String? = null
)
