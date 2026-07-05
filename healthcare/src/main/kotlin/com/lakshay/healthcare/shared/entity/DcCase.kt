package com.lakshay.healthcare.shared.entity

import com.lakshay.healthcare.shared.lifecycle.CaseStatus
import jakarta.persistence.*

@Entity
@Table(name = "DC_CASES")
data class DcCase(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_no")
    val caseNo: Long = 0,
    @Column(name = "app_id")
    val appId: Long,
    @Column(name = "plan_id")
    val planId: Long? = null,
    @Column(name = "case_status")
    @Enumerated(EnumType.STRING)
    val caseStatus: CaseStatus = CaseStatus.SUBMITTED
)
