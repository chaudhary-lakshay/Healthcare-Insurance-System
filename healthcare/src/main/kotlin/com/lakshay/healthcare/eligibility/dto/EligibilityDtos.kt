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
