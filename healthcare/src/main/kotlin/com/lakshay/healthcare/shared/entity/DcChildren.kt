package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "DC_CHILDREN")
data class DcChildren(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "child_id")
    val childId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long,
    @Column(name = "child_dob")
    val childDOB: LocalDate? = null,
    @Column(name = "child_ssn")
    val childSSN: Long? = null
)
