package com.lakshay.healthcare.casework.service

import com.lakshay.healthcare.casework.dto.AssignmentRequest
import com.lakshay.healthcare.casework.dto.DocumentReviewRequest
import com.lakshay.healthcare.casework.dto.DocumentReviewResponse
import com.lakshay.healthcare.casework.dto.DocumentSummaryResponse
import com.lakshay.healthcare.casework.dto.AssignmentResponse
import com.lakshay.healthcare.casework.dto.CaseNoteRequest
import com.lakshay.healthcare.casework.dto.CaseNoteResponse
import com.lakshay.healthcare.casework.dto.QueueItemResponse
import com.lakshay.healthcare.casework.dto.RfiRequest
import com.lakshay.healthcare.casework.dto.RfiResponse
import com.lakshay.healthcare.shared.audit.AuditService
import com.lakshay.healthcare.shared.entity.CaseAssignment
import com.lakshay.healthcare.shared.entity.CaseNote
import com.lakshay.healthcare.shared.exception.DuplicateResourceException
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.exception.ValidationException
import com.lakshay.healthcare.shared.lifecycle.CaseStatus
import com.lakshay.healthcare.shared.notification.NotificationService
import com.lakshay.healthcare.shared.notification.StatusEmailService
import com.lakshay.healthcare.shared.repository.CaseAssignmentRepository
import com.lakshay.healthcare.shared.repository.CaseNoteRepository
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.DocumentRepository
import com.lakshay.healthcare.shared.repository.WorkerMasterRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CaseworkService(
    private val caseNoteRepository: CaseNoteRepository,
    private val dcCaseRepository: DcCaseRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val caseAssignmentRepository: CaseAssignmentRepository,
    private val workerRepository: WorkerMasterRepository,
    private val notificationService: NotificationService,
    private val documentRepository: DocumentRepository,
    private val auditService: AuditService,
    private val statusEmailService: StatusEmailService
) {

    private val ssnPattern = Regex("""\d{3}-?\d{2}-?\d{4}""")

    // Worker requests info from the citizen. The recipient is derived from the case (never a request
    // param), the message is rejected if it contains an SSN, and notificationSent reports whether the
    // portal notice actually went out (notifyPortal swallows its own failures).
    fun requestInfo(caseNo: Long, request: RfiRequest): RfiResponse {
        val case = dcCaseRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Case not found: $caseNo")
        if (request.message.isBlank()) throw ValidationException("message is required")
        if (ssnPattern.containsMatchIn(request.message)) throw ValidationException("message must not contain an SSN")
        val citizen = citizenRepository.findByAppId(case.appId)
            ?: throw ResourceNotFoundException("No citizen on file for case: $caseNo")
        val citizenEmail = citizen.email
        val author = SecurityContextHolder.getContext().authentication?.name ?: "SYSTEM"
        val notice = notificationService.notifyPortal(
            caseNo = caseNo, recipient = citizenEmail, noticeType = "RFI",
            subject = "Information requested - Case #$caseNo", body = request.message
        )
        caseNoteRepository.save(CaseNote(caseNo = caseNo, author = author, body = "RFI: ${request.message}"))
        auditService.record("RFI_SENT", "DcCase", caseNo.toString())
        statusEmailService.rfiOpened(caseNo, citizenEmail, citizen.fullName, request.message)
        return RfiResponse(caseNo = caseNo, notificationSent = notice != null)
    }

    fun listDocuments(caseNo: Long): List<DocumentSummaryResponse> =
        documentRepository.findByCaseNo(caseNo).map {
            DocumentSummaryResponse(it.docId, it.docType, it.fileName, it.contentType, it.status, it.createdAt.toString())
        }

    @Transactional
    fun reviewDocument(docId: Long, request: DocumentReviewRequest): DocumentReviewResponse {
        val decision = request.decision.uppercase()
        if (decision !in setOf("REVIEWED", "REJECTED")) throw ValidationException("decision must be REVIEWED or REJECTED")
        if (!documentRepository.existsById(docId)) throw ResourceNotFoundException("Document not found: $docId")
        documentRepository.updateStatus(docId, decision)
        auditService.record("DOCUMENT_REVIEWED", "Document", docId.toString(), "decision=$decision")
        return DocumentReviewResponse(docId, decision)
    }

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
