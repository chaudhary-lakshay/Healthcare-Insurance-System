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

data class NoticeResponse(
    val noticeId: Long,
    val noticeType: String,
    val subject: String? = null,
    val body: String? = null,
    val status: String,
    val createdAt: String
)

data class CitizenApplyRequest(
    val fullName: String,
    val gender: String,
    val ssn: Long,
    val phoneNo: Long? = null,
    val dob: String? = null,
    val attested: Boolean = false
)

data class ApplyResponse(
    val appId: Long,
    val caseNo: Long,
    val stateName: String,
    val caseStatus: String
)

data class DocumentResponse(
    val docId: Long,
    val docType: String,
    val fileName: String? = null,
    val contentType: String? = null,
    val status: String,
    val createdAt: String
)
