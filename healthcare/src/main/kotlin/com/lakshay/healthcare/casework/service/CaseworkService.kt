package com.lakshay.healthcare.casework.service

import com.lakshay.healthcare.casework.dto.CaseNoteRequest
import com.lakshay.healthcare.casework.dto.CaseNoteResponse
import com.lakshay.healthcare.casework.dto.QueueItemResponse
import com.lakshay.healthcare.shared.audit.AuditService
import com.lakshay.healthcare.shared.entity.CaseNote
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.exception.ValidationException
import com.lakshay.healthcare.shared.lifecycle.CaseStatus
import com.lakshay.healthcare.shared.repository.CaseNoteRepository
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class CaseworkService(
    private val caseNoteRepository: CaseNoteRepository,
    private val dcCaseRepository: DcCaseRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val auditService: AuditService
) {
    fun addNote(caseNo: Long, request: CaseNoteRequest): Long {
        dcCaseRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Case not found: $caseNo")
        if (request.body.isBlank()) throw ValidationException("note body is required")
        val author = SecurityContextHolder.getContext().authentication?.name ?: "SYSTEM"
        val saved = caseNoteRepository.save(CaseNote(caseNo = caseNo, author = author, body = request.body))
        auditService.record("CASE_NOTE_ADDED", "DcCase", caseNo.toString())
        return saved.noteId
    }

    fun listNotes(caseNo: Long): List<CaseNoteResponse> =
        caseNoteRepository.findByCaseNoOrderByCreatedAtDesc(caseNo).map {
            CaseNoteResponse(it.noteId, it.author, it.body, it.createdAt.toString())
        }

    fun queue(statusRaw: String): List<QueueItemResponse> {
        val status = runCatching { CaseStatus.valueOf(statusRaw.uppercase()) }
            .getOrElse { throw ValidationException("invalid case status: $statusRaw") }
        return dcCaseRepository.findByCaseStatus(status).map { case ->
            val name = citizenRepository.findByAppId(case.appId)?.fullName
            QueueItemResponse(case.caseNo, case.caseStatus.name, name)
        }
    }
}
