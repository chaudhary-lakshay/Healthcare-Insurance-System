package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// A caseworker's free-text note on a case. Author is the staff member's email (from the JWT).
// Body is staff-authored and may describe the citizen — treat as PII (retention follow-up).
@Entity
@Table(name = "CASE_NOTE")
data class CaseNote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "note_id")
    val noteId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long,
    @Column(name = "author")
    val author: String,
    @Column(name = "body")
    val body: String,
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
