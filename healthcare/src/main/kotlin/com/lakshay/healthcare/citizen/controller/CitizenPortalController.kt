package com.lakshay.healthcare.citizen.controller

import com.lakshay.healthcare.citizen.dto.ApplyResponse
import com.lakshay.healthcare.citizen.dto.CaseStatusResponse
import com.lakshay.healthcare.citizen.dto.CitizenApplyRequest
import com.lakshay.healthcare.citizen.dto.DocumentResponse
import com.lakshay.healthcare.citizen.dto.NoticeResponse
import com.lakshay.healthcare.citizen.service.CitizenPortalService
import com.lakshay.healthcare.user.dto.RegisterRequest
import com.lakshay.healthcare.user.service.UserMgmtService
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

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

    @GetMapping("/cases")
    fun myCases(): ResponseEntity<List<CaseStatusResponse>> =
        ResponseEntity.ok(citizenPortalService.getMyCases())

    @GetMapping("/cases/{caseNo}/status")
    fun myCaseStatus(@PathVariable caseNo: Long): ResponseEntity<CaseStatusResponse> =
        ResponseEntity.ok(citizenPortalService.getMyCaseStatus(caseNo))

    @GetMapping("/notices")
    fun myNotices(): ResponseEntity<List<NoticeResponse>> =
        ResponseEntity.ok(citizenPortalService.getMyNotices())

    @PostMapping("/applications")
    fun apply(@RequestBody request: CitizenApplyRequest): ResponseEntity<ApplyResponse> =
        ResponseEntity.ok(citizenPortalService.apply(request))

    @PostMapping("/cases/{caseNo}/documents")
    fun upload(
        @PathVariable caseNo: Long,
        @RequestParam docType: String,
        @RequestParam file: MultipartFile
    ): ResponseEntity<DocumentResponse> =
        ResponseEntity.ok(citizenPortalService.uploadDocument(caseNo, docType, file))

    @GetMapping("/cases/{caseNo}/documents")
    fun listDocuments(@PathVariable caseNo: Long): ResponseEntity<List<DocumentResponse>> =
        ResponseEntity.ok(citizenPortalService.listDocuments(caseNo))

    @GetMapping("/documents/{docId}")
    fun download(@PathVariable docId: Long): ResponseEntity<ByteArray> {
        val doc = citizenPortalService.downloadDocument(docId)
        val ext = when (doc.contentType) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> "bin"
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, doc.contentType ?: "application/octet-stream")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document_${doc.docId}.$ext\"")
            .body(doc.content)
    }
}
