package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "PLAN_CATEGORY")
data class PlanCategory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    val categoryId: Long = 0,
    @Column(name = "category_name")
    val categoryName: String,
    @Column(name = "active_sw")
    val activeSW: String = "Y",
    @Column(name = "created_date")
    val createdDate: LocalDate = LocalDate.now(),
    @Column(name = "updated_date")
    val updatedDate: LocalDate = LocalDate.now(),
    @Column(name = "created_by")
    val createdBy: String? = null,
    @Column(name = "updated_by")
    val updatedBy: String? = null
)
