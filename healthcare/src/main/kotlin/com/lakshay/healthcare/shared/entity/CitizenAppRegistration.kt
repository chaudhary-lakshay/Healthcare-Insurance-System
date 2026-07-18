package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "CITIZEN_APPLICATION")
data class CitizenAppRegistration(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "app_id")
    val appId: Long = 0,
    @Column(name = "full_name", length = 100)
    val fullName: String,
    @Column(name = "email", length = 100)
    val email: String,
    @Column(name = "gender")
    val gender: String,
    @Column(name = "phone_no")
    val phoneNo: Long? = null,
    @Column(name = "ssn")
    val ssn: Long,
    @Column(name = "state_name", length = 100)
    val stateName: String,
    @Column(name = "dob")
    val dob: LocalDate? = null,
    @Column(name = "remark", length = 1000)
    val remark: String? = null,
    @Column(name = "created_by", length = 100)
    val createdBy: String? = null,
    @Column(name = "updated_by", length = 100)
    val updatedBy: String? = null,
    @Column(name = "creation_date")
    val creationDate: LocalDate = LocalDate.now(),
    @Column(name = "updation_date")
    val updationDate: LocalDate = LocalDate.now()
)
