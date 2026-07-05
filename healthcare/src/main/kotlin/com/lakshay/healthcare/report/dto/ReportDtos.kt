package com.lakshay.healthcare.report.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDate

data class ReportRequest(
    val reportType: String,
    val reportFormat: String = "TEXT",
    val periodCovered: String? = null,
    val generatedFor: String? = null,
    val departmentName: String? = null
)

data class ReportResponse(
    val reportId: Long = 0,
    val reportName: String = "",
    val reportType: String? = null,
    val reportFormat: String? = null,
    val reportStatus: String? = null,
    val reportDescription: String? = null,
    val reportFilePath: String? = null,
    val generatedFor: String? = null,
    val departmentName: String? = null,
    val periodCovered: String? = null,
    @JsonFormat(pattern = "yyyy-MM-dd")
    val createdDate: LocalDate? = null,
    val createdBy: String? = null,
    val downloadUrl: String? = null
)
