package com.lakshay.healthcare.application.controller

import com.lakshay.healthcare.application.dto.CitizenRegistrationRequest
import com.lakshay.healthcare.application.dto.CitizenRegistrationResponse
import com.lakshay.healthcare.application.service.CitizenApplicationRegistrationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/CitizenAR-api")
class CitizenApplicationController(
    private val registrationService: CitizenApplicationRegistrationService
) {

    @PostMapping("/save")
    fun registerCitizen(@RequestBody request: CitizenRegistrationRequest): ResponseEntity<CitizenRegistrationResponse> {
        val response = registrationService.registerCitizen(request)
        return ResponseEntity.ok(response)
    }
}
