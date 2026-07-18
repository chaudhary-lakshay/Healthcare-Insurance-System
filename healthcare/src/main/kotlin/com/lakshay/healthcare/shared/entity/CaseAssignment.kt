package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// One current assignment per case (UNIQUE case_no). Reassignment updates this row.
// assigned_to / assigned_by are staff emails (matches CaseNote.author convention).
@Entity
@Table(name = "CASE_ASSIGNMENT")
data class CaseAssignment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    val assignmentId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long,
    @Column(name = "assigned_to")
    val assignedTo: String,
    @Column(name = "assigned_by")
    val assignedBy: String? = null,
    @Column(name = "assigned_at")
    val assignedAt: LocalDateTime = LocalDateTime.now()
)
