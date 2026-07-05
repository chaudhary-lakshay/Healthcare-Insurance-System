package com.lakshay.healthcare.casework.dto

data class CaseNoteRequest(
    val body: String
)

data class CaseNoteResponse(
    val noteId: Long,
    val author: String,
    val body: String,
    val createdAt: String
)

data class QueueItemResponse(
    val caseNo: Long,
    val caseStatus: String,
    val citizenName: String? = null
)

data class AssignmentRequest(
    val assignedTo: String
)

data class AssignmentResponse(
    val caseNo: Long,
    val assignedTo: String,
    val assignedBy: String? = null,
    val assignedAt: String
)

data class RfiRequest(
    val message: String
)

data class RfiResponse(
    val caseNo: Long,
    val notificationSent: Boolean
)

data class DocumentSummaryResponse(
    val docId: Long,
    val docType: String,
    val fileName: String? = null,
    val contentType: String? = null,
    val status: String,
    val createdAt: String
)

data class DocumentReviewRequest(
    val decision: String
)

data class DocumentReviewResponse(
    val docId: Long,
    val status: String
)
