package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.CaseNote
import org.springframework.data.jpa.repository.JpaRepository

interface CaseNoteRepository : JpaRepository<CaseNote, Long> {
    fun findByCaseNoOrderByCreatedAtDesc(caseNo: Long): List<CaseNote>
}
