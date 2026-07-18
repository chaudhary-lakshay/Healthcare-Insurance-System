package com.lakshay.healthcare.benefit.dto

data class BenefitResponse(
    val message: String,
    val jobId: Long?,
    val status: String? = null
)
