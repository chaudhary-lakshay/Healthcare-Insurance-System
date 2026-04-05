package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*

@Entity
@Table(name = "DC_INCOME")
data class DcIncome(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "income_id")
    val incomeId: Long = 0,
    @Column(name = "case_no")
    val caseNo: Long,
    @Column(name = "emp_income")
    val empIncome: Double? = null,
    @Column(name = "property_income")
    val propertyIncome: Double? = null
)
