package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.EligibilityDetails
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EligibilityDetailsRepository : JpaRepository<com.lakshay.healthcare.shared.entity.EligibilityDetails, Long> {
    fun findByCaseNo(caseNo: Long): com.lakshay.healthcare.shared.entity.EligibilityDetails?
    fun findAllByPlanStatus(planStatus: String): List<com.lakshay.healthcare.shared.entity.EligibilityDetails>
    fun findByPlanStatus(planStatus: String, pageable: Pageable): Page<com.lakshay.healthcare.shared.entity.EligibilityDetails>
}
