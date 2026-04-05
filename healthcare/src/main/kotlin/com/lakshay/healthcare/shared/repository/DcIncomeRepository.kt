package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.DcIncome
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DcIncomeRepository : JpaRepository<DcIncome, Long> {
    fun findByCaseNo(caseNo: Long): DcIncome?
}
