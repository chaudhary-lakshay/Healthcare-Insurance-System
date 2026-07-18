package com.lakshay.healthcare.shared.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

// Templated status-change emails, delivered through NotificationService so every
// send leaves a Notice row. Model is a whitelist — no SSN, no entities, ever.
// Nothing here throws: a mail hiccup must not block the state change that caused it.
@Service
class StatusEmailService(
    private val templateEngine: SpringTemplateEngine,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(StatusEmailService::class.java)

    fun caseStatusChanged(
        caseNo: Long,
        recipient: String,
        citizenName: String,
        status: String,
        planName: String?,
        benefitAmt: Double?,
        denialReason: String?
    ) {
        val subject = when (status) {
            "APPROVED" -> "Application approved - Case #$caseNo"
            "DENIED" -> "Application decision - Case #$caseNo"
            else -> "Case update - Case #$caseNo"
        }
        send(
            caseNo, recipient, "STATUS_CHANGE", subject,
            mapOf(
                "citizenName" to citizenName, "caseNo" to caseNo, "status" to status,
                "planName" to planName, "benefitAmt" to benefitAmt,
                "denialReason" to denialReason, "message" to null
            )
        )
    }

    fun rfiOpened(caseNo: Long, recipient: String, citizenName: String, message: String) {
        send(
            caseNo, recipient, "RFI", "Information requested - Case #$caseNo",
            mapOf(
                "citizenName" to citizenName, "caseNo" to caseNo, "status" to "RFI",
                "planName" to null, "benefitAmt" to null, "denialReason" to null,
                "message" to message
            )
        )
    }

    private fun send(caseNo: Long, recipient: String, type: String, subject: String, model: Map<String, Any?>) {
        try {
            val html = templateEngine.process("mail/status-change", Context().apply { setVariables(model) })
            notificationService.notifyEmail(caseNo, recipient, type, subject, html)
        } catch (e: Exception) {
            // caseNo only — email addresses stay out of logs
            log.error("Status email failed for case {}", caseNo, e)
        }
    }
}
