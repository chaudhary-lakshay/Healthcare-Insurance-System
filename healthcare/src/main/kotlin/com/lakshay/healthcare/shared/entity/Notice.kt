package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// A notice to a recipient. created_at is immutable (val) — appeal deadlines will key off it later.
// Keep body minimal: the recipient's own non-sensitive info only, never an SSN or anyone else's PII.
@Entity
@Table(name = "NOTICE")
data class Notice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    val noticeId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long? = null,
    @Column(name = "recipient")
    val recipient: String,
    @Column(name = "channel")
    val channel: String,
    @Column(name = "notice_type")
    val noticeType: String,
    @Column(name = "subject")
    val subject: String? = null,
    @Column(name = "body")
    val body: String? = null,
    @Column(name = "status")
    val status: String = "PENDING",
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "sent_at")
    val sentAt: LocalDateTime? = null
)
