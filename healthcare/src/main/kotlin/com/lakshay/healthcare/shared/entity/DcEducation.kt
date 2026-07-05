package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*

@Entity
@Table(name = "DC_EDUCATION")
data class DcEducation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "education_id")
    val educationId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long,
    @Column(name = "highest_qlfy")
    val highestQlfy: String? = null,
    @Column(name = "pass_out_year")
    val passOutYear: Int? = null
)
