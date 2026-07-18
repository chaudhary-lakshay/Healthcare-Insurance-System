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
import com.lakshay.healthcare.shared.entity.Notice
import com.lakshay.healthcare.shared.exception.ForbiddenException
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.exception.ValidationException
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.DocumentRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.shared.repository.NoticeRepository
import com.lakshay.healthcare.shared.security.OwnershipService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
// LongParameterList: Spring constructor injection — each dependency is a distinct bean
@Suppress("LongParameterList")
class CitizenPortalService(
    private val ownershipService: OwnershipService,
    private val dcCaseRepository: DcCaseRepository,
    private val eligibilityRepository: EligibilityDetailsRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val noticeRepository: NoticeRepository,
    private val documentRepository: DocumentRepository,
    private val applicationService: CitizenApplicationRegistrationService,
    private val auditService: AuditService
) {

    private val allowedContentTypes = setOf("application/pdf", "image/jpeg", "image/png")
    private val allowedDocTypes = setOf("ID", "INCOME", "RESIDENCY", "OTHER")

    // An RFI is "open" until the citizen acts on it. FAILED never reached them, so it isn't open.
    private fun isOpenRfi(n: Notice): Boolean =
        n.noticeType == "RFI" && n.status != "RESOLVED" && n.status != "FAILED"

    // Upload a document to the citizen's OWN case (ownership-checked). Validates type/size; stores bytes.
    // Uploading also clears any open RFI on the case. NOTE: docType is not matched to the RFI's
    // requested type yet — for now ANY accepted upload resolves all open RFIs on the case.
    @Transactional
    fun uploadDocument(caseNo: Long, docType: String, file: MultipartFile): DocumentResponse {
        ownershipService.assertCanAccessCase(caseNo)
        val validationError = when {
            file.isEmpty -> "file is required"
            docType.uppercase() !in allowedDocTypes -> "invalid docType: $docType"
            file.contentType !in allowedContentTypes -> "unsupported content type: ${file.contentType}"
            else -> null
        }
        if (validationError != null) throw ValidationException(validationError)
        val email = SecurityContextHolder.getContext().authentication?.name ?: "SYSTEM"
        val saved = documentRepository.save(
            Document(
                caseNo = caseNo,
                uploadedBy = email,
                docType = docType.uppercase(),
                fileName = file.originalFilename,
                contentType = file.contentType,
                content = file.bytes
            )
        )
        auditService.record("DOCUMENT_UPLOADED", "Document", saved.docId.toString(), "type=${docType.uppercase()}")

        val openRfis = noticeRepository.findByCaseNo(caseNo).filter { isOpenRfi(it) }
        if (openRfis.isNotEmpty()) {
            openRfis.forEach { noticeRepository.save(it.copy(status = "RESOLVED")) }
            auditService.record(
                "RFI_RESOLVED",
                "DcCase",
                caseNo.toString(),
                "resolvedBy=docUpload count=${openRfis.size}"
            )
        }
        return DocumentResponse(
            saved.docId,
            saved.docType,
            saved.fileName,
            saved.contentType,
            saved.status,
            saved.createdAt.toString()
        )
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
        val actionRequired = noticeRepository.findByCaseNo(caseNo).any { isOpenRfi(it) }
        auditService.record("CITIZEN_CASE_VIEWED", "DcCase", caseNo.toString())
        return CaseStatusResponse(
            caseNo = caseNo,
            caseStatus = case.caseStatus.name,
            planName = elig?.planName,
            planStatus = elig?.planStatus,
            benefitAmt = elig?.benefitAmt,
            denialReason = elig?.denialReason,
            planStartDate = elig?.planStartDate?.toString(),
            planEndDate = elig?.planEndDate?.toString(),
            actionRequired = actionRequired
        )
    }

    // Every case whose application email matches the caller's JWT. Email comes from the token only,
    // so the filter itself is the ownership boundary — no per-row assertCanAccessCase needed.
    fun getMyCases(): List<CaseStatusResponse> {
        val email = SecurityContextHolder.getContext().authentication?.name
            ?: throw ForbiddenException("No authenticated user")
        val appIds = citizenRepository.findByEmail(email).map { it.appId }
        if (appIds.isEmpty()) return emptyList()
        val cases = appIds.mapNotNull { dcCaseRepository.findByAppId(it) }
        // One query for the citizen's notices; build the set of cases with an open RFI.
        val rfiCaseNos = noticeRepository.findByRecipientOrderByCreatedAtDesc(email)
            .filter { isOpenRfi(it) && it.caseNo != null }
            .mapNotNull { it.caseNo }
            .toSet()
        auditService.record("CITIZEN_CASES_LISTED", "DcCase", null, "count=${cases.size}")
        return cases.map { case ->
            val elig = eligibilityRepository.findByCaseNo(case.caseNo)
            CaseStatusResponse(
                caseNo = case.caseNo,
                caseStatus = case.caseStatus.name,
                planName = elig?.planName,
                planStatus = elig?.planStatus,
                benefitAmt = elig?.benefitAmt,
                denialReason = elig?.denialReason,
                planStartDate = elig?.planStartDate?.toString(),
                planEndDate = elig?.planEndDate?.toString(),
                actionRequired = case.caseNo in rfiCaseNos
            )
        }
    }

    fun getMyNotices(): List<NoticeResponse> {
        val email = SecurityContextHolder.getContext().authentication?.name
            ?: throw ForbiddenException("No authenticated user")
        auditService.record("NOTICE_INBOX_VIEWED", "Notice", null, "own inbox")
        return noticeRepository.findByRecipientOrderByCreatedAtDesc(email).map {
            NoticeResponse(
                it.noticeId,
                it.noticeType,
                it.subject,
                it.body,
                it.status,
                it.createdAt.toString(),
                it.caseNo
            )
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
                fullName = request.fullName,
                email = email,
                gender = request.gender,
                phoneNo = request.phoneNo,
                ssn = request.ssn,
                dob = request.dob
            )
        )
        val caseNo = dcCaseRepository.save(DcCase(appId = reg.appId)).caseNo
        auditService.record("CITIZEN_APPLICATION_SUBMITTED", "DcCase", caseNo.toString(), "attested=true")
        return ApplyResponse(appId = reg.appId, caseNo = caseNo, stateName = reg.stateName, caseStatus = "SUBMITTED")
    }
}
