package com.lakshay.healthcare.application.dto

data class CitizenRegistrationRequest(
    val fullName: String,
    val email: String,
    val gender: String,
    val phoneNo: Long? = null,
    val ssn: Long,
    val dob: String? = null
)

data class CitizenRegistrationResponse(
    val appId: Long,
    val stateName: String
)
