package com.lakshay.healthcare.benefit.controller

import com.lakshay.healthcare.benefit.dto.BenefitResponse
import com.lakshay.healthcare.benefit.service.BenefitLaunchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/bi-api")
class BenefitController(
    private val benefitLaunchService: BenefitLaunchService
) {

    @GetMapping("/send")
    fun sendBenefits(): ResponseEntity<BenefitResponse> {
        return try {
            val execution = benefitLaunchService.launchBenefitIssuance()
            ResponseEntity.ok(
                BenefitResponse(
                    message = "Benefit issuance job completed",
                    jobId = execution.jobId,
                    status = execution.status.toString()
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                BenefitResponse(
                    message = "Failed: ${e.message}",
                    jobId = null,
                    status = "FAILED"
                )
            )
        }
    }
}
