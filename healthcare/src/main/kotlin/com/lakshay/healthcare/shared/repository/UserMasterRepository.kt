package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.UserMaster
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserMasterRepository : JpaRepository<com.lakshay.healthcare.shared.entity.UserMaster, Long> {
    fun findByEmail(email: String): com.lakshay.healthcare.shared.entity.UserMaster?
    fun findByEmailAndPassword(email: String, password: String): com.lakshay.healthcare.shared.entity.UserMaster?
}
