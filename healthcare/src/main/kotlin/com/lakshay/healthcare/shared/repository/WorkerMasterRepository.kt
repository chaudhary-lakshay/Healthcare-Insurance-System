package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.WorkerMaster
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WorkerMasterRepository : JpaRepository<com.lakshay.healthcare.shared.entity.WorkerMaster, Long> {
    fun findByEmail(email: String): com.lakshay.healthcare.shared.entity.WorkerMaster?
    fun findByEmailAndPassword(email: String, password: String): com.lakshay.healthcare.shared.entity.WorkerMaster?
}
