package com.lakshay.healthcare.application.controller

import com.lakshay.healthcare.application.dto.CaseTimelineResponse
import com.lakshay.healthcare.application.dto.CitizenRegistrationRequest
import com.lakshay.healthcare.application.dto.CitizenRegistrationResponse
import com.lakshay.healthcare.application.service.CaseTimelineService
import com.lakshay.healthcare.application.service.CitizenApplicationRegistrationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/CitizenAR-api")
class CitizenApplicationController(
    private val registrationService: CitizenApplicationRegistrationService,
    private val caseTimelineService: CaseTimelineService
) {

    @PostMapping("/save")
    fun registerCitizen(@RequestBody request: CitizenRegistrationRequest): ResponseEntity<CitizenRegistrationResponse> {
        val response = registrationService.registerCitizen(request)
        return ResponseEntity.ok(response)
    }

    // authenticated by default (no permitAll entry); OwnershipService gates owner-vs-staff
    @GetMapping("/timeline/{caseNo}")
    fun caseTimeline(@PathVariable caseNo: Long): ResponseEntity<CaseTimelineResponse> {
        return ResponseEntity.ok(caseTimelineService.timeline(caseNo))
    }
}
