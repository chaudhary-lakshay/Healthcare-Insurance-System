package com.lakshay.healthcare.data.dto

data class IncomeRequest(
    val caseNo: Long,
    val empIncome: Double? = null,
    val propertyIncome: Double? = null
)

data class EducationRequest(
    val caseNo: Long,
    val highestQlfy: String? = null,
    val passOutYear: Int? = null
)

data class ChildrenRequest(
    val caseNo: Long,
    val childDOB: String? = null,
    val childSSN: Long? = null
)

data class PlanSelectionRequest(
    val caseNo: Long,
    val planId: Long
)

data class CaseResponse(
    val caseNo: Long
)

data class PlanNameResponse(
    val planId: Long,
    val planName: String
)

data class DcSummaryResponse(
    val caseNo: Long,
    val planName: String? = null,
    val citizenName: String? = null,
    val citizenSsn: Long? = null,
    val income: IncomeRequest? = null,
    val education: EducationRequest? = null,
    val children: List<ChildrenRequest> = emptyList()
)
