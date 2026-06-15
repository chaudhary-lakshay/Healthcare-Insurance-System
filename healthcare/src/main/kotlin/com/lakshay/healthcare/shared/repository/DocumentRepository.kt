package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.Document
import org.springframework.data.jpa.repository.JpaRepository

interface DocumentRepository : JpaRepository<Document, Long> {
    fun findByCaseNo(caseNo: Long): List<DocumentMeta>
}
