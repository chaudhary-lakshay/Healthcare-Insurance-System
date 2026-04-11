package com.lakshay.healthcare.user.dto

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val password: String,
    val email: String,
    val mobileNo: Long? = null,
    val ssn: Long? = null,
    val gender: String,
    val dob: String? = null,
    val designation: String? = null,
    val helpCenterName: String? = null,
    val helpCenterLocation: String? = null
)

data class ActivateRequest(
    val email: String,
    val tempPassword: String,
    val newPassword: String
)

data class LoginResponse(
    val token: String,
    val role: String,
    val userId: Long? = null,
    val workerId: Long? = null
)
