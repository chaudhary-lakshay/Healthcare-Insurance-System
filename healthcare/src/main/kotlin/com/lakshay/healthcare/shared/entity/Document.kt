package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime

// An uploaded document (identity/income/residency proof). Bytes are stored in-DB as LONGBLOB,
// matching the CoTrigger.coNoticePdf precedent. Highest-sensitivity table — retention/deletion
// and encryption-at-rest are tracked follow-ups.
@Entity
@Table(name = "DOCUMENT")
data class Document(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doc_id")
    val docId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long,
    @Column(name = "uploaded_by")
    val uploadedBy: String,
    @Column(name = "doc_type")
    val docType: String,
    @Column(name = "file_name")
    val fileName: String? = null,
    @Column(name = "content_type")
    val contentType: String? = null,
    @Lob
    @Column(name = "content", columnDefinition = "LONGBLOB")
    val content: ByteArray,
    @Column(name = "status")
    val status: String = "UPLOADED",
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
