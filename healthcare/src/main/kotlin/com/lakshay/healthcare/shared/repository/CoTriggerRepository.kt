package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.CoTrigger
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CoTriggerRepository : JpaRepository<com.lakshay.healthcare.shared.entity.CoTrigger, Long> {
    fun findByCaseNo(caseNo: Long): com.lakshay.healthcare.shared.entity.CoTrigger?
    fun findByTriggerStatus(triggerStatus: String): List<com.lakshay.healthcare.shared.entity.CoTrigger>
}
