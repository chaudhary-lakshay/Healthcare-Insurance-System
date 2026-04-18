package com.lakshay.healthcare.correspondence.service

import com.lakshay.healthcare.correspondence.dto.TriggerResponse
import com.lakshay.healthcare.shared.entity.CoTrigger
import com.lakshay.healthcare.shared.entity.EligibilityDetails
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.CoTriggerRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.shared.util.EmailUtils
import com.lowagie.text.Document
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream

@Service
class CorrespondenceService(
    private val coTriggerRepository: CoTriggerRepository,
    private val eligibilityRepository: EligibilityDetailsRepository,
    private val dcCaseRepository: DcCaseRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val emailUtils: EmailUtils
) {

    private val logger = LoggerFactory.getLogger(CorrespondenceService::class.java)

    fun processTriggers(): List<TriggerResponse> {
        val pendingTriggers = coTriggerRepository.findByTriggerStatus("PENDING")
        val responses = mutableListOf<TriggerResponse>()

        var successCount = 0
        var failCount = 0

        for (trigger in pendingTriggers) {
            try {
                processTrigger(trigger)
                successCount++
                responses.add(TriggerResponse(trigger.triggerId, trigger.caseNo, "PROCESSED"))
            } catch (e: Exception) {
                logger.error("Failed to process trigger ${trigger.triggerId}", e)
                failCount++
                coTriggerRepository.save(trigger.copy(triggerStatus = "FAILED"))
                responses.add(TriggerResponse(trigger.triggerId, trigger.caseNo, "FAILED"))
            }
        }

        logger.info("Triggers processed: success=$successCount, failed=$failCount, total=${pendingTriggers.size}")
        return responses
    }

    private fun processTrigger(trigger: CoTrigger) {
        logger.info("Processing trigger for case number: {}", trigger.caseNo)

        val eligibility = eligibilityRepository.findByCaseNo(trigger.caseNo)
            ?: throw IllegalStateException("No eligibility details found for case ${trigger.caseNo}")

        val dcCase = dcCaseRepository.findByCaseNo(trigger.caseNo)
            ?: throw IllegalStateException("No case found for case number ${trigger.caseNo}")

        val citizen = citizenRepository.findByAppId(dcCase.appId)
            ?: throw IllegalStateException("No citizen found for appId ${dcCase.appId}")

        val pdfBytes = generateBenefitPdf(eligibility)

        val body = "Hello ${citizen.fullName},<br><br>This email contains complete details about your plan approval or denial.<br><br>Please see the attached PDF for details."
        val emailSent = emailUtils.sendEmailWithAttachment(
            subject = "Plan Approval/Denial Notification - Case #${trigger.caseNo}",
            body = body,
            to = citizen.email,
            attachmentName = "benefit_notice_${trigger.caseNo}.pdf",
            attachmentData = pdfBytes
        )
        if (!emailSent) {
            throw IllegalStateException("Email delivery failed for case ${trigger.caseNo}")
        }

        val updatedTrigger = trigger.copy(
            coNoticePdf = pdfBytes,
            triggerStatus = "PROCESSED"
        )
        coTriggerRepository.save(updatedTrigger)

        logger.info("Successfully processed trigger for case number: {}", trigger.caseNo)
    }

    private fun generateBenefitPdf(eligibility: EligibilityDetails): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4)
        PdfWriter.getInstance(document, outputStream)
        document.open()

        val titleFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 20f)
        val title = Paragraph("Plan Approval/Denial Communication", titleFont)
        title.alignment = Paragraph.ALIGN_CENTER
        document.add(title)
        document.add(Paragraph(" "))

        val table = PdfPTable(6)
        table.widthPercentage = 90f
        table.setSpacingBefore(10f)

        val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD)

        fun addHeaderCell(text: String) {
            val cell = PdfPCell(Phrase(text, headerFont))
            cell.backgroundColor = Color.LIGHT_GRAY
            cell.setPadding(5f)
            table.addCell(cell)
        }

        addHeaderCell("Case No")
        addHeaderCell("Holder Name")
        addHeaderCell("Plan Name")
        addHeaderCell("Plan Status")
        addHeaderCell("Benefit Amt")
        addHeaderCell("Denial Reason")

        table.addCell(eligibility.caseNo.toString())
        table.addCell(eligibility.holderName ?: "N/A")
        table.addCell(eligibility.planName ?: "N/A")
        table.addCell(eligibility.planStatus ?: "N/A")
        table.addCell(eligibility.benefitAmt?.toString() ?: "N/A")
        table.addCell(eligibility.denialReason ?: "N/A")

        document.add(table)
        document.close()
        return outputStream.toByteArray()
    }
}
