package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CitizenAppRegistrationRepository : JpaRepository<CitizenAppRegistration, Long> {
    fun findBySsn(ssn: Long): CitizenAppRegistration?
    fun findByAppId(appId: Long): CitizenAppRegistration?
    fun findByEmail(email: String): List<CitizenAppRegistration>
}
