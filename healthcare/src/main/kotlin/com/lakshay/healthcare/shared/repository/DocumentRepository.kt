package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.Document
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface DocumentRepository : JpaRepository<Document, Long> {
    fun findByCaseNo(caseNo: Long): List<DocumentMeta>

    // Status-only update so a review doesn't load the LONGBLOB into memory.
    @Modifying
    @Transactional
    @Query("update Document d set d.status = :status where d.docId = :docId")
    fun updateStatus(@Param("docId") docId: Long, @Param("status") status: String): Int
}
