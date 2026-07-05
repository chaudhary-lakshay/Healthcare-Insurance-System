package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "WORKER_MASTER")
data class WorkerMaster(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worker_id")
    val workerId: Long = 0,
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
    @Column(name = "designation")
    val designation: String? = null,
    @Column(name = "help_center_name")
    val helpCenterName: String? = null,
    @Column(name = "help_center_location")
    val helpCenterLocation: String? = null,
    @Column(name = "active_sw")
    val activeSw: String = "N",
    @Column(name = "role")
    val role: String = "WORKER",
    @Column(name = "created_on")
    val createdOn: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_on")
    val updatedOn: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created_by")
    val createdBy: String? = null,
    @Column(name = "updated_by")
    val updatedBy: String? = null
)
