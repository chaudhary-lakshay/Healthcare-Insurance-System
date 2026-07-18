package com.lakshay.healthcare.correspondence.controller

import com.lakshay.healthcare.correspondence.dto.TriggerResponse
import com.lakshay.healthcare.correspondence.service.CorrespondenceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/co-triggers-api")
class CorrespondenceController(
    private val correspondenceService: com.lakshay.healthcare.correspondence.service.CorrespondenceService
) {

    @GetMapping("/process")
    fun processTriggers(): ResponseEntity<List<TriggerResponse>> {
        val responses = correspondenceService.processTriggers()
        return ResponseEntity.ok(responses)
    }
}
