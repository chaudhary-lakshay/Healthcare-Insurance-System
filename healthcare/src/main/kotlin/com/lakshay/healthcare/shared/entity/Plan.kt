package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "PLAN_MASTER")
data class Plan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    val planId: Long = 0,
    @Column(name = "plan_name")
    val planName: String,
    @Column(name = "start_date")
    val startDate: LocalDate? = null,
    @Column(name = "end_date")
    val endDate: LocalDate? = null,
    @Column(name = "description")
    val description: String? = null,
    @Column(name = "category_id")
    val categoryId: Long? = null,
    @Column(name = "active_sw")
    val activeSw: String = "Y",
    @Column(name = "creation_date")
    val creationDate: LocalDate = LocalDate.now(),
    @Column(name = "updation_date")
    val updationDate: LocalDate = LocalDate.now(),
    @Column(name = "created_by")
    val createdBy: String? = null,
    @Column(name = "updated_by")
    val updatedBy: String? = null
)
