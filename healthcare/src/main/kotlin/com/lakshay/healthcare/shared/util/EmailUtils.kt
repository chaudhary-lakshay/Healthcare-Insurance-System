package com.lakshay.healthcare.shared.util

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

@Component
class EmailUtils(
    private val mailSender: JavaMailSender
) {

    private val logger = LoggerFactory.getLogger(EmailUtils::class.java)

    // TooGenericExceptionCaught: any send failure -> false, caller decides. Never throws.
    @Suppress("TooGenericExceptionCaught")
    fun sendEmail(subject: String, body: String, to: String): Boolean {
        return try {
            val mimeMessage: MimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true)
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(body, true)
            mailSender.send(mimeMessage)
            logger.info("Email sent successfully to: $to")
            true
        } catch (e: Exception) {
            logger.error("Failed to send email to: $to", e)
            false
        }
    }

    // TooGenericExceptionCaught: any send failure -> false, caller decides. Never throws.
    @Suppress("TooGenericExceptionCaught")
    fun sendEmailWithAttachment(
        subject: String,
        body: String,
        to: String,
        attachmentName: String,
        attachmentData: ByteArray
    ): Boolean {
        return try {
            val mimeMessage: MimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true)
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(body, true)
            helper.addAttachment(attachmentName, org.springframework.core.io.ByteArrayResource(attachmentData))
            mailSender.send(mimeMessage)
            logger.info("Email with attachment sent successfully to: $to")
            true
        } catch (e: Exception) {
            logger.error("Failed to send email with attachment to: $to", e)
            false
        }
    }
}
