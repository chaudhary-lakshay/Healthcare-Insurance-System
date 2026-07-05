package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*

@Entity
@Table(name = "CO_TRIGGERS")
data class CoTrigger(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trigger_id")
    val triggerId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long,
    @Lob
    @Column(name = "co_notice_pdf", columnDefinition = "LONGBLOB")
    val coNoticePdf: ByteArray? = null,
    @Column(name = "trigger_status")
    val triggerStatus: String? = "PENDING"
)
