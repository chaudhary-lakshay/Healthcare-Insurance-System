package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.lifecycle.CaseStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DcCaseRepository : JpaRepository<DcCase, Long> {
    fun findByAppId(appId: Long): DcCase?
    fun findByCaseNo(caseNo: Long): DcCase?
    fun findByCaseStatus(caseStatus: CaseStatus): List<DcCase>
}
