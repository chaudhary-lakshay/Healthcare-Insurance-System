package com.lakshay.healthcare.shared.audit

import com.lakshay.healthcare.shared.entity.AuditEvent
import com.lakshay.healthcare.shared.repository.AuditEventRepository
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

// Writes the append-only audit trail. record() never throws — a failed audit write must not break
// the business action it describes. NEVER put PII (SSN, DOB, names, account numbers) in `detail`;
// use ids and status codes only.
@Service
class AuditService(private val auditRepository: AuditEventRepository) {

    private val log = LoggerFactory.getLogger(AuditService::class.java)

    // TooGenericExceptionCaught: audit writes must never break the caller — log and swallow.
    @Suppress("TooGenericExceptionCaught")
    fun record(action: String, entityType: String? = null, entityId: String? = null, detail: String? = null) {
        try {
            val auth = SecurityContextHolder.getContext().authentication
            auditRepository.save(
                AuditEvent(
                    actor = auth?.name ?: "SYSTEM",
                    actorRole = auth?.authorities?.firstOrNull()?.authority,
                    action = action,
                    entityType = entityType,
                    entityId = entityId,
                    detail = detail
                )
            )
        } catch (e: Exception) {
            log.error("Failed to write audit event {} for {}:{}", action, entityType, entityId, e)
        }
    }
}
