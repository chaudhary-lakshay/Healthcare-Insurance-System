package com.lakshay.healthcare.report.service

import com.lakshay.healthcare.report.dto.ReportRequest
import com.lakshay.healthcare.report.dto.ReportResponse
import com.lakshay.healthcare.shared.entity.GovernmentReport
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.shared.repository.GovernmentReportRepository
import com.lakshay.healthcare.shared.repository.PlanRepository
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Service
import org.springframework.util.FileCopyUtils
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@Service
class GovernmentReportService(
    private val reportRepository: GovernmentReportRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val eligibilityRepository: EligibilityDetailsRepository,
    private val planRepository: PlanRepository
) {

    fun generateReport(request: ReportRequest): ReportResponse {
        val totalApplications = citizenRepository.count()
        val approvedCount = eligibilityRepository.findAllByPlanStatus("APPROVED").size
        val deniedCount = eligibilityRepository.findAllByPlanStatus("DENIED").size
        val totalPlans = planRepository.count()

        val reportContent = buildReportContent(request, totalApplications, approvedCount, deniedCount, totalPlans)

        val reportName = "${request.reportType}_${request.periodCovered ?: LocalDate.now().month}_${System.currentTimeMillis()}"

        val report = GovernmentReport(
            reportName = reportName,
            reportType = request.reportType,
            reportFormat = request.reportFormat,
            reportStatus = "GENERATED",
            reportDescription = "Government report for ${request.periodCovered ?: "current period"}",
            reportFilePath = "reports/government/${reportName}.${request.reportFormat.lowercase()}",
            generatedFor = request.generatedFor ?: "Department of Health",
            departmentName = request.departmentName ?: "Health Insurance Department",
            periodCovered = request.periodCovered ?: LocalDate.now().month.toString(),
            reportContent = reportContent,
            createdBy = "SYSTEM",
            updatedBy = "SYSTEM"
        )

        val saved = reportRepository.save(report)
        return toResponse(saved)
    }

    fun listAllReports(): List<ReportResponse> {
        return reportRepository.findAll().map { toResponse(it) }
    }

    fun getReportById(reportId: Long): ReportResponse {
        val report = reportRepository.findById(reportId)
            .orElseThrow { ResourceNotFoundException("Report not found: $reportId") }
        return toResponse(report)
    }

    fun getReportsByType(reportType: String): List<ReportResponse> {
        return reportRepository.findByReportType(reportType).map { toResponse(it) }
    }

    fun getReportsByDepartment(departmentName: String): List<ReportResponse> {
        return reportRepository.findByDepartmentName(departmentName).map { toResponse(it) }
    }

    fun getReportsByPeriod(periodCovered: String): List<ReportResponse> {
        return reportRepository.findByPeriodCovered(periodCovered).map { toResponse(it) }
    }

    fun deleteReport(reportId: Long) {
        if (!reportRepository.existsById(reportId)) {
            throw ResourceNotFoundException("Report not found: $reportId")
        }
        reportRepository.deleteById(reportId)
    }

    fun downloadReport(reportId: Long, response: HttpServletResponse) {
        val report = reportRepository.findById(reportId)
            .orElseThrow { ResourceNotFoundException("Report not found: $reportId") }

        val contentType = when (report.reportFormat?.lowercase()) {
            "pdf" -> "application/pdf"
            "excel", "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "text/plain"
        }
        response.contentType = contentType
        response.setHeader("Content-Disposition", "attachment; filename=\"${report.reportName}.${(report.reportFormat ?: "txt").lowercase()}\"")

        val content = report.reportContent?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        FileCopyUtils.copy(content, response.outputStream)
    }

    private fun buildReportContent(
        request: ReportRequest,
        totalApplications: Long,
        approvedCount: Int,
        deniedCount: Int,
        totalPlans: Long
    ): String {
        return buildString {
            appendLine("GOVERNMENT REPORT")
            appendLine("Type: ${request.reportType}")
            appendLine("Period: ${request.periodCovered ?: "Current"}")
            appendLine("Generated: ${LocalDate.now()}")
            appendLine("=" .repeat(50))
            appendLine()
            appendLine("APPLICATION STATISTICS")
            appendLine("Total Applications: $totalApplications")
            appendLine("Approved: $approvedCount")
            appendLine("Denied: $deniedCount")
            appendLine("Approval Rate: ${if (totalApplications > 0) "%.1f".format(approvedCount.toDouble() / totalApplications * 100) else "0"}%")
            appendLine()
            appendLine("PLAN STATISTICS")
            appendLine("Total Plans: $totalPlans")
            appendLine()
            appendLine("BENEFIT STATISTICS")
            appendLine("Total Eligible Citizens: $approvedCount")
            appendLine("Total Denied: $deniedCount")
            appendLine("=" .repeat(50))
            appendLine("End of Report")
        }
    }

    private fun toResponse(entity: GovernmentReport): ReportResponse {
        return ReportResponse(
            reportId = entity.reportId,
            reportName = entity.reportName,
            reportType = entity.reportType,
            reportFormat = entity.reportFormat,
            reportStatus = entity.reportStatus,
            reportDescription = entity.reportDescription,
            reportFilePath = entity.reportFilePath,
            generatedFor = entity.generatedFor,
            departmentName = entity.departmentName,
            periodCovered = entity.periodCovered,
            createdDate = entity.createdDate,
            createdBy = entity.createdBy,
            downloadUrl = "/report-api/government/download/${entity.reportId}"
        )
    }
}
