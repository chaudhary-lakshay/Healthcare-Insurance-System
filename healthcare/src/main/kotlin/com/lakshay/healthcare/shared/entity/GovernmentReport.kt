package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "ISH_GOVERNMENT_REPORTS")
data class GovernmentReport(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    val reportId: Long = 0,
    @Column(name = "report_name")
    val reportName: String,
    @Column(name = "report_type")
    val reportType: String? = null,
    @Column(name = "report_format")
    val reportFormat: String? = null,
    @Column(name = "report_status")
    val reportStatus: String? = null,
    @Column(name = "report_description")
    val reportDescription: String? = null,
    @Column(name = "report_file_path")
    val reportFilePath: String? = null,
    @Column(name = "generated_for")
    val generatedFor: String? = null,
    @Column(name = "department_name")
    val departmentName: String? = null,
    @Column(name = "period_covered")
    val periodCovered: String? = null,
    @Lob
    @Column(name = "report_content", columnDefinition = "LONGTEXT")
    val reportContent: String? = null,
    @Column(name = "created_date")
    val createdDate: LocalDate = LocalDate.now(),
    @Column(name = "updated_date")
    val updatedDate: LocalDate = LocalDate.now(),
    @Column(name = "created_by")
    val createdBy: String? = null,
    @Column(name = "updated_by")
    val updatedBy: String? = null
)
