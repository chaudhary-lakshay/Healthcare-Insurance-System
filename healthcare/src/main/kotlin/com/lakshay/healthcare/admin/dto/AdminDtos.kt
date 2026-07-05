package com.lakshay.healthcare.admin.dto

data class PlanRequest(
    val planName: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val description: String? = null,
    val categoryId: Long? = null
)

data class PlanCategoryResponse(
    val categoryId: Long,
    val categoryName: String
)

data class PlanResponse(
    val planId: Long,
    val planName: String
)

data class PlanDataResponse(
    val planId: Long = 0,
    val planName: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val description: String? = null,
    val categoryId: Long? = null,
    val activeSw: String? = null,
    val createdBy: String? = null
)

data class UserDataResponse(
    val userId: Long = 0,
    val name: String = "",
    val email: String = "",
    val mobileNo: Long? = null,
    val ssn: Long? = null,
    val gender: String = "",
    val activeSw: String? = null,
    val role: String? = null
)

data class WorkerDataResponse(
    val workerId: Long = 0,
    val name: String = "",
    val email: String = "",
    val mobileNo: Long? = null,
    val ssn: Long? = null,
    val gender: String = "",
    val designation: String? = null,
    val activeSw: String? = null,
    val role: String? = null
)
