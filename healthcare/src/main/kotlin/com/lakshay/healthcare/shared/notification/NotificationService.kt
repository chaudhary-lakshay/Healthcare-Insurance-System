package com.lakshay.healthcare.shared.notification

import com.lakshay.healthcare.shared.entity.Notice
import com.lakshay.healthcare.shared.repository.NoticeRepository
import com.lakshay.healthcare.shared.util.EmailUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

// Sends notices and keeps a record of each. PORTAL notices are just rows the citizen portal reads;
// EMAIL notices also go out via EmailUtils. Neither method throws — a notice failure must never
// break the business action that triggered it. Keep bodies minimal: no SSN, no other person's PII.
@Service
class NotificationService(
    private val noticeRepository: NoticeRepository,
    private val emailUtils: EmailUtils
) {
    private val log = LoggerFactory.getLogger(NotificationService::class.java)

    // Drop a notice in the recipient's portal inbox. The row itself is the delivery, so it's SENT.
    // TooGenericExceptionCaught: notification failures are logged, never propagated to the caller.
    @Suppress("TooGenericExceptionCaught")
    fun notifyPortal(caseNo: Long?, recipient: String, noticeType: String, subject: String, body: String): Notice? =
        try {
            noticeRepository.save(
                Notice(
                    caseNo = caseNo,
                    recipient = recipient,
                    channel = "PORTAL",
                    noticeType = noticeType,
                    subject = subject,
                    body = body,
                    status = "SENT",
                    sentAt = LocalDateTime.now()
                )
            )
        } catch (e: Exception) {
            log.error("Failed to write portal notice {} for {}", noticeType, recipient, e)
            null
        }

    // Email a notice and record whether it went out.
    // TooGenericExceptionCaught: notification failures are logged, never propagated to the caller.
    @Suppress("TooGenericExceptionCaught")
    fun notifyEmail(caseNo: Long?, recipient: String, noticeType: String, subject: String, body: String): Notice? =
        try {
            val saved = noticeRepository.save(
                Notice(
                    caseNo = caseNo,
                    recipient = recipient,
                    channel = "EMAIL",
                    noticeType = noticeType,
                    subject = subject,
                    body = body,
                    status = "PENDING"
                )
            )
            val ok = emailUtils.sendEmail(subject, body, recipient)
            noticeRepository.save(saved.copy(status = if (ok) "SENT" else "FAILED", sentAt = LocalDateTime.now()))
        } catch (e: Exception) {
            log.error("Failed to send email notice {} for {}", noticeType, recipient, e)
            null
        }
}
