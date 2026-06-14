package com.lakshay.healthcare.citizen.controller

import com.lakshay.healthcare.citizen.dto.CaseStatusResponse
import com.lakshay.healthcare.citizen.dto.NoticeResponse
import com.lakshay.healthcare.citizen.service.CitizenPortalService
import com.lakshay.healthcare.user.dto.RegisterRequest
import com.lakshay.healthcare.user.service.UserMgmtService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/citizen-api")
class CitizenPortalController(
    private val userService: UserMgmtService,
    private val citizenPortalService: CitizenPortalService
) {
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Map<String, Any>> {
        val result = userService.registerCitizen(request)
        return ResponseEntity.ok(
            mapOf(
                "message" to "Citizen registered. Check email for the temporary password to activate.",
                "userId" to result.id
            )
        )
    }

    @GetMapping("/cases/{caseNo}/status")
    fun myCaseStatus(@PathVariable caseNo: Long): ResponseEntity<CaseStatusResponse> =
        ResponseEntity.ok(citizenPortalService.getMyCaseStatus(caseNo))

    @GetMapping("/notices")
    fun myNotices(): ResponseEntity<List<NoticeResponse>> =
        ResponseEntity.ok(citizenPortalService.getMyNotices())
}
