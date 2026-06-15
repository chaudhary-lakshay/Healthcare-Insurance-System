package com.lakshay.healthcare.shared.repository

import java.time.LocalDateTime

// Metadata-only projection so listing documents never pulls the LONGBLOB content into memory.
interface DocumentMeta {
    val docId: Long
    val docType: String
    val fileName: String?
    val contentType: String?
    val status: String
    val createdAt: LocalDateTime
}
