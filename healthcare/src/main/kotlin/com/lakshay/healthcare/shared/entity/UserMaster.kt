package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "USER_MASTER")
data class UserMaster(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    val userId: Long = 0,
    @Column(name = "name")
    val name: String,
    @Column(name = "password")
    val password: String,
    @Column(name = "email")
    val email: String,
    @Column(name = "mobile_no")
    val mobileNo: Long? = null,
    @Column(name = "ssn")
    val ssn: Long? = null,
    @Column(name = "gender")
    val gender: String,
    @Column(name = "dob")
    val dob: LocalDate? = null,
    @Column(name = "active_sw")
    val activeSw: String = "N",
    @Column(name = "role")
    val role: String = "USER",
    @Column(name = "created_on")
    val createdOn: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_on")
    val updatedOn: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created_by")
    val createdBy: String? = null,
    @Column(name = "updated_by")
    val updatedBy: String? = null
)
