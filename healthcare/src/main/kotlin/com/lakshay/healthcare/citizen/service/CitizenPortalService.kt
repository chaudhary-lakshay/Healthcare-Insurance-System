package com.lakshay.healthcare.citizen.service

import com.lakshay.healthcare.citizen.dto.CaseStatusResponse
import com.lakshay.healthcare.citizen.dto.NoticeResponse
import com.lakshay.healthcare.shared.audit.AuditService
import com.lakshay.healthcare.shared.exception.ForbiddenException
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.shared.repository.NoticeRepository
import com.lakshay.healthcare.shared.security.OwnershipService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class CitizenPortalService(
    private val ownershipService: OwnershipService,
    private val dcCaseRepository: DcCaseRepository,
    private val eligibilityRepository: EligibilityDetailsRepository,
    private val noticeRepository: NoticeRepository,
    private val auditService: AuditService
) {
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
}
