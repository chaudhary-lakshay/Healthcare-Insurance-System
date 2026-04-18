package com.lakshay.healthcare.eligibility.controller

import com.lakshay.healthcare.eligibility.dto.EligibilityResponse
import com.lakshay.healthcare.eligibility.service.EligibilityDeterminationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ed-api")
class EligibilityController(
    private val eligibilityService: EligibilityDeterminationService
) {

    @GetMapping("/determine/{caseNo}")
    fun determineEligibility(@PathVariable caseNo: Long): ResponseEntity<EligibilityResponse> {
        val response = eligibilityService.determineEligibility(caseNo)
        return ResponseEntity.ok(response)
    }
}
