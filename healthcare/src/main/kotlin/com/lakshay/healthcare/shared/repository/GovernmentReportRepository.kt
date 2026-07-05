package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.GovernmentReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GovernmentReportRepository : JpaRepository<GovernmentReport, Long> {
    fun findByReportType(reportType: String): List<GovernmentReport>
    fun findByDepartmentName(departmentName: String): List<GovernmentReport>
    fun findByPeriodCovered(periodCovered: String): List<GovernmentReport>
}
