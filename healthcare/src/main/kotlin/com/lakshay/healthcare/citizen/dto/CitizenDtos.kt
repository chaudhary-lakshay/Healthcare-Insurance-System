package com.lakshay.healthcare.citizen.dto

// Minimal, citizen-safe view of their OWN case. No SSN, no household PII.
data class CaseStatusResponse(
    val caseNo: Long,
    val caseStatus: String,
    val planName: String? = null,
    val planStatus: String? = null,
    val benefitAmt: Double? = null,
    val denialReason: String? = null,
    val planStartDate: String? = null,
    val planEndDate: String? = null
)
