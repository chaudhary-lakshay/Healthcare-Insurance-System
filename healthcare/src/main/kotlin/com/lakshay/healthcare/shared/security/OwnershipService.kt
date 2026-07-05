package com.lakshay.healthcare.shared.security

import com.lakshay.healthcare.shared.audit.AuditService
import com.lakshay.healthcare.shared.exception.ForbiddenException
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

// The single gate for "can the current principal touch this case?". Staff (ADMIN/WORKER) see all.
// Anyone else may only touch a case whose applicant email matches their own JWT subject. The match
// is by email, not role, so it doesn't matter whether the token says CITIZEN or USER — only the
// verified email decides. EVERY citizen-facing read/write must call this; never trust role/path alone.
@Service
class OwnershipService(
    private val dcCaseRepository: DcCaseRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val auditService: AuditService
) {
    private val staffRoles = setOf("ROLE_ADMIN", "ROLE_WORKER")

    fun assertCanAccessCase(caseNo: Long) {
        val auth = SecurityContextHolder.getContext().authentication
        val role = auth?.authorities?.firstOrNull()?.authority
        if (role in staffRoles) return

        val email = auth?.name
        val ownerEmail = dcCaseRepository.findByCaseNo(caseNo)
            ?.let { citizenRepository.findByAppId(it.appId)?.email }
        if (email != null && ownerEmail != null && email == ownerEmail) return

        // Uniform denial whether the case is missing or just not theirs — don't leak existence.
        auditService.record("CASE_ACCESS_DENIED", "DcCase", caseNo.toString(), "role=$role")
        throw ForbiddenException("Not authorized for this case")
    }
}
