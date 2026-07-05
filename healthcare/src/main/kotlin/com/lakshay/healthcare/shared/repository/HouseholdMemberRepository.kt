package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.HouseholdMember
import org.springframework.data.jpa.repository.JpaRepository

interface HouseholdMemberRepository : JpaRepository<HouseholdMember, Long> {
    fun findByCaseNo(caseNo: Long): List<HouseholdMember>
}
