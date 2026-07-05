package com.lakshay.healthcare.eligibility.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDate

data class EligibilityResponse(
    val planStatus: String?,
    val planName: String?,
    val benefitAmt: Double?,
    val denialReason: String?,
    @JsonFormat(pattern = "yyyy-MM-dd")
    val planStartDate: LocalDate? = null,
    @JsonFormat(pattern = "yyyy-MM-dd")
    val planEndDate: LocalDate? = null
)

// Advisory multi-program screening result. Whitelisted fields only — no SSN/PII.
data class ScreeningResponse(
    val caseNo: Long,
    val programs: List<ProgramResult>
)

data class ProgramResult(
    val planName: String,
    val planStatus: String?,
    val benefitAmt: Double? = null,
    val denialReason: String? = null
)
