package com.lakshay.healthcare.citizen.service

import com.lakshay.healthcare.application.dto.CitizenRegistrationRequest
import com.lakshay.healthcare.application.service.CitizenApplicationRegistrationService
import com.lakshay.healthcare.citizen.dto.ApplyResponse
import com.lakshay.healthcare.citizen.dto.CaseStatusResponse
import com.lakshay.healthcare.citizen.dto.CitizenApplyRequest
import com.lakshay.healthcare.citizen.dto.DocumentResponse
import com.lakshay.healthcare.citizen.dto.NoticeResponse
import com.lakshay.healthcare.shared.audit.AuditService
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.entity.Document
import com.lakshay.healthcare.shared.exception.ForbiddenException
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.exception.ValidationException
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.shared.repository.DocumentRepository
import com.lakshay.healthcare.shared.repository.NoticeRepository
import com.lakshay.healthcare.shared.security.OwnershipService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class CitizenPortalService(
    private val ownershipService: OwnershipService,
    private val dcCaseRepository: DcCaseRepository,
    private val eligibilityRepository: EligibilityDetailsRepository,
    private val noticeRepository: NoticeRepository,
    private val documentRepository: DocumentRepository,
    private val applicationService: CitizenApplicationRegistrationService,
    private val auditService: AuditService
) {

    private val allowedContentTypes = setOf("application/pdf", "image/jpeg", "image/png")
    private val allowedDocTypes = setOf("ID", "INCOME", "RESIDENCY", "OTHER")

    // Upload a document to the citizen's OWN case (ownership-checked). Validates type/size; stores bytes.
    fun uploadDocument(caseNo: Long, docType: String, file: MultipartFile): DocumentResponse {
        ownershipService.assertCanAccessCase(caseNo)
        if (file.isEmpty) throw ValidationException("file is required")
        if (docType.uppercase() !in allowedDocTypes) throw ValidationException("invalid docType: $docType")
        if (file.contentType !in allowedContentTypes) throw ValidationException("unsupported content type: ${file.contentType}")
        val email = SecurityContextHolder.getContext().authentication?.name ?: "SYSTEM"
        val saved = documentRepository.save(
            Document(
                caseNo = caseNo, uploadedBy = email, docType = docType.uppercase(),
                fileName = file.originalFilename, contentType = file.contentType, content = file.bytes
            )
        )
        auditService.record("DOCUMENT_UPLOADED", "Document", saved.docId.toString(), "type=${docType.uppercase()}")
        return DocumentResponse(saved.docId, saved.docType, saved.fileName, saved.contentType, saved.status, saved.createdAt.toString())
    }

    // List the citizen's own documents for a case (metadata only — no bytes loaded).
    fun listDocuments(caseNo: Long): List<DocumentResponse> {
        ownershipService.assertCanAccessCase(caseNo)
        return documentRepository.findByCaseNo(caseNo).map {
            DocumentResponse(it.docId, it.docType, it.fileName, it.contentType, it.status, it.createdAt.toString())
        }
    }

    // Download a document. Ownership is re-derived from the document's own case, not a path param.
    fun downloadDocument(docId: Long): Document {
        val doc = documentRepository.findById(docId)
            .orElseThrow { ResourceNotFoundException("Document not found: $docId") }
        ownershipService.assertCanAccessCase(doc.caseNo)
        auditService.record("DOCUMENT_VIEWED", "Document", docId.toString())
        return doc
    }
    fun getMyCaseStatus(caseNo: Long): CaseStatusResponse {
        ownershipService.assertCanAccessCase(caseNo)
        val case = dcCaseRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Case not found: $caseNo")
        val elig = eligibilityRepository.findByCaseNo(caseNo)
        auditService.record("CITIZEN_CASE_VIEWED", "DcCase", caseNo.toString())
        return CaseStatusResponse(
            caseNo = caseNo,
            caseStatus = case.caseStatus.name,
            planName = elig?.planName,
            planStatus = elig?.planStatus,
            benefitAmt = elig?.benefitAmt,
            denialReason = elig?.denialReason,
            planStartDate = elig?.planStartDate?.toString(),
            planEndDate = elig?.planEndDate?.toString()
        )
    }

    fun getMyNotices(): List<NoticeResponse> {
        val email = SecurityContextHolder.getContext().authentication?.name
            ?: throw ForbiddenException("No authenticated user")
        auditService.record("NOTICE_INBOX_VIEWED", "Notice", null, "own inbox")
        return noticeRepository.findByRecipientOrderByCreatedAtDesc(email).map {
            NoticeResponse(it.noticeId, it.noticeType, it.subject, it.body, it.status, it.createdAt.toString())
        }
    }

    // Citizen self-apply. The applicant email comes from the JWT, never the request body, so a
    // citizen can only ever apply as themselves. Requires an e-sign attestation to submit.
    fun apply(request: CitizenApplyRequest): ApplyResponse {
        val email = SecurityContextHolder.getContext().authentication?.name
            ?: throw ForbiddenException("No authenticated user")
        if (!request.attested) throw ValidationException("attestation is required to submit an application")
        val reg = applicationService.registerCitizen(
            CitizenRegistrationRequest(
                fullName = request.fullName, email = email, gender = request.gender,
                phoneNo = request.phoneNo, ssn = request.ssn, dob = request.dob
            )
        )
        val caseNo = dcCaseRepository.save(DcCase(appId = reg.appId)).caseNo
        auditService.record("CITIZEN_APPLICATION_SUBMITTED", "DcCase", caseNo.toString(), "attested=true")
        return ApplyResponse(appId = reg.appId, caseNo = caseNo, stateName = reg.stateName, caseStatus = "SUBMITTED")
    }
}
