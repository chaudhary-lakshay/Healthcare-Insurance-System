package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CitizenAppRegistrationRepository : JpaRepository<com.lakshay.healthcare.shared.entity.CitizenAppRegistration, Long> {
    fun findBySsn(ssn: Long): com.lakshay.healthcare.shared.entity.CitizenAppRegistration?
    fun findByAppId(appId: Long): com.lakshay.healthcare.shared.entity.CitizenAppRegistration?
    fun findByEmail(email: String): List<com.lakshay.healthcare.shared.entity.CitizenAppRegistration>
}
