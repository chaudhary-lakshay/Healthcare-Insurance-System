package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.AdminMaster
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AdminMasterRepository : JpaRepository<AdminMaster, Long> {
    fun findByEmail(email: String): AdminMaster?
    fun findByEmailAndPassword(email: String, password: String): AdminMaster?
}
