package com.lakshay.healthcare.report.controller

import com.lakshay.healthcare.report.dto.ReportRequest
import com.lakshay.healthcare.report.dto.ReportResponse
import com.lakshay.healthcare.report.service.GovernmentReportService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/report-api/government")
class ReportController(
    private val reportService: GovernmentReportService
) {

    @PostMapping("/generate")
    fun generateReport(@RequestBody request: ReportRequest): ResponseEntity<ReportResponse> {
        val response = reportService.generateReport(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listAllReports(): ResponseEntity<List<ReportResponse>> {
        val reports = reportService.listAllReports()
        return ResponseEntity.ok(reports)
    }

    @GetMapping("/{reportId}")
    fun getReportById(@PathVariable reportId: Long): ResponseEntity<ReportResponse> {
        val report = reportService.getReportById(reportId)
        return ResponseEntity.ok(report)
    }

    @GetMapping("/type/{reportType}")
    fun getReportsByType(@PathVariable reportType: String): ResponseEntity<List<ReportResponse>> {
        val reports = reportService.getReportsByType(reportType)
        return ResponseEntity.ok(reports)
    }

    @GetMapping("/department")
    fun getReportsByDepartment(@RequestParam departmentName: String): ResponseEntity<List<ReportResponse>> {
        val reports = reportService.getReportsByDepartment(departmentName)
        return ResponseEntity.ok(reports)
    }

    @GetMapping("/period")
    fun getReportsByPeriod(@RequestParam periodCovered: String): ResponseEntity<List<ReportResponse>> {
        val reports = reportService.getReportsByPeriod(periodCovered)
        return ResponseEntity.ok(reports)
    }

    @GetMapping("/download/{reportId}")
    fun downloadReport(@PathVariable reportId: Long, response: HttpServletResponse) {
        reportService.downloadReport(reportId, response)
    }

    @DeleteMapping("/{reportId}")
    fun deleteReport(@PathVariable reportId: Long): ResponseEntity<Void> {
        reportService.deleteReport(reportId)
        return ResponseEntity.noContent().build()
    }
}
