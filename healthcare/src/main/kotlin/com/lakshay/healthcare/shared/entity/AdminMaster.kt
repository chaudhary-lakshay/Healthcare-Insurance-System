package com.lakshay.healthcare.shared.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "ADMIN_MASTER")
data class AdminMaster(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    val adminId: Long = 0,
    @Column(name = "name")
    val name: String,
    @Column(name = "password")
    val password: String,
    @Column(name = "email")
    val email: String,
    @Column(name = "role")
    val role: String = "ADMIN",
    @Column(name = "active_sw")
    val activeSw: String = "Y",
    @Column(name = "created_on")
    val createdOn: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_on")
    val updatedOn: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created_by")
    val createdBy: String? = null,
    @Column(name = "updated_by")
    val updatedBy: String? = null
)
