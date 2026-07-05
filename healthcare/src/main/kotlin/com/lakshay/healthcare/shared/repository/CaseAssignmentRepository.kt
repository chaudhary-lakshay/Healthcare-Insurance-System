package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.CaseAssignment
import org.springframework.data.jpa.repository.JpaRepository

interface CaseAssignmentRepository : JpaRepository<CaseAssignment, Long> {
    fun findByCaseNo(caseNo: Long): CaseAssignment?
}
