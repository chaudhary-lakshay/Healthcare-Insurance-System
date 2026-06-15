package com.lakshay.healthcare.casework.service

import com.lakshay.healthcare.casework.dto.AssignmentRequest
import com.lakshay.healthcare.casework.dto.AssignmentResponse
import com.lakshay.healthcare.casework.dto.CaseNoteRequest
import com.lakshay.healthcare.casework.dto.CaseNoteResponse
import com.lakshay.healthcare.casework.dto.QueueItemResponse
import com.lakshay.healthcare.shared.audit.AuditService
import com.lakshay.healthcare.shared.entity.CaseAssignment
import com.lakshay.healthcare.shared.entity.CaseNote
import com.lakshay.healthcare.shared.exception.DuplicateResourceException
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.exception.ValidationException
import com.lakshay.healthcare.shared.lifecycle.CaseStatus
import com.lakshay.healthcare.shared.repository.CaseAssignmentRepository
import com.lakshay.healthcare.shared.repository.CaseNoteRepository
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.WorkerMasterRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CaseworkService(
    private val caseNoteRepository: CaseNoteRepository,
    private val dcCaseRepository: DcCaseRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val caseAssignmentRepository: CaseAssignmentRepository,
    private val workerRepository: WorkerMasterRepository,
    private val auditService: AuditService
) {

    fun assign(caseNo: Long, request: AssignmentRequest): AssignmentResponse {
        dcCaseRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Case not found: $caseNo")
        val worker = workerRepository.findByEmail(request.assignedTo)
        if (worker == null || worker.activeSw != "Y") {
            throw ValidationException("assignee is not an active worker: ${request.assignedTo}")
        }
        val assignedBy = SecurityContextHolder.getContext().authentication?.name ?: "SYSTEM"
        val existing = caseAssignmentRepository.findByCaseNo(caseNo)
        val toSave = existing?.copy(assignedTo = request.assignedTo, assignedBy = assignedBy, assignedAt = LocalDateTime.now())
            ?: CaseAssignment(caseNo = caseNo, assignedTo = request.assignedTo, assignedBy = assignedBy)
        val saved = try {
            caseAssignmentRepository.save(toSave)
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateResourceException("Assignment was modified concurrently; retry")
        }
        auditService.record("CASE_ASSIGNED", "DcCase", caseNo.toString(), "to=${request.assignedTo}")
        return AssignmentResponse(saved.caseNo, saved.assignedTo, saved.assignedBy, saved.assignedAt.toString())
    }

    fun getAssignment(caseNo: Long): AssignmentResponse {
        val a = caseAssignmentRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("No assignment for case: $caseNo")
        return AssignmentResponse(a.caseNo, a.assignedTo, a.assignedBy, a.assignedAt.toString())
    }
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
