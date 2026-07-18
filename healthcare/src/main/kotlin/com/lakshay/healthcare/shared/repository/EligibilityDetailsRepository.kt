package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.EligibilityDetails
import java.time.LocalDate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EligibilityDetailsRepository : JpaRepository<EligibilityDetails, Long> {
    fun findByCaseNo(caseNo: Long): EligibilityDetails?
    fun findByPlanStatusAndPlanEndDate(planStatus: String, planEndDate: LocalDate): List<EligibilityDetails>
    fun findAllByPlanStatus(planStatus: String): List<EligibilityDetails>
    fun findByPlanStatus(planStatus: String, pageable: Pageable): Page<EligibilityDetails>
}
