package com.lakshay.healthcare.application.service

import com.lakshay.healthcare.application.dto.CitizenRegistrationRequest
import com.lakshay.healthcare.application.dto.CitizenRegistrationResponse
import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.ssa.service.SsnValidationService
import com.lakshay.healthcare.shared.exception.InvalidSsnException
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class CitizenApplicationRegistrationService(
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val ssnValidationService: SsnValidationService
) {

    fun registerCitizen(request: CitizenRegistrationRequest): CitizenRegistrationResponse {
        val stateName = ssnValidationService.validateSsn(request.ssn)

        val citizen = CitizenAppRegistration(
            fullName = request.fullName,
            email = request.email,
            gender = request.gender,
            phoneNo = request.phoneNo,
            ssn = request.ssn,
            stateName = stateName,
            dob = request.dob?.let { LocalDate.parse(it) },
            createdBy = request.fullName,
            updatedBy = request.fullName
        )

        val saved = citizenRepository.save(citizen)
        return CitizenRegistrationResponse(appId = saved.appId, stateName = saved.stateName)
    }
}
