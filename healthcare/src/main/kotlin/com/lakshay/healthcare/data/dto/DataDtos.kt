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
    val children: List<ChildrenRequest> = emptyList(),
    val householdMembers: List<HouseholdMemberResponse> = emptyList(),
    // applicant (1) + non-applicant household members. Independent of the legacy children list above.
    val householdSize: Int = 1
)

data class HouseholdMemberRequest(
    val caseNo: Long,
    val fullName: String? = null,
    val relationship: String,
    val dob: String? = null,
    val memberIncome: Double? = null
)

data class HouseholdMemberResponse(
    val memberId: Long,
    val caseNo: Long,
    val fullName: String? = null,
    val relationship: String,
    val dob: String? = null,
    val memberIncome: Double? = null
)
