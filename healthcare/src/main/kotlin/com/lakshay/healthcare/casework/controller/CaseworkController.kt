package com.lakshay.healthcare.casework.controller

import com.lakshay.healthcare.casework.dto.AssignmentRequest
import com.lakshay.healthcare.casework.dto.AssignmentResponse
import com.lakshay.healthcare.casework.dto.DocumentReviewRequest
import com.lakshay.healthcare.casework.dto.DocumentReviewResponse
import com.lakshay.healthcare.casework.dto.DocumentSummaryResponse
import com.lakshay.healthcare.casework.dto.CaseNoteRequest
import com.lakshay.healthcare.casework.dto.CaseNoteResponse
import com.lakshay.healthcare.casework.dto.QueueItemResponse
import com.lakshay.healthcare.casework.dto.RfiRequest
import com.lakshay.healthcare.casework.dto.RfiResponse
import com.lakshay.healthcare.casework.service.CaseworkService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/casework-api")
class CaseworkController(
    private val caseworkService: CaseworkService
) {
    @PostMapping("/cases/{caseNo}/notes")
    fun addNote(@PathVariable caseNo: Long, @RequestBody request: CaseNoteRequest): ResponseEntity<Map<String, Any>> {
        val noteId = caseworkService.addNote(caseNo, request)
        return ResponseEntity.ok(mapOf("message" to "Note added", "noteId" to noteId))
    }

    @GetMapping("/cases/{caseNo}/notes")
    fun listNotes(@PathVariable caseNo: Long): ResponseEntity<List<CaseNoteResponse>> =
        ResponseEntity.ok(caseworkService.listNotes(caseNo))

    @GetMapping("/queue")
    fun queue(@RequestParam(defaultValue = "SUBMITTED") status: String): ResponseEntity<List<QueueItemResponse>> =
        ResponseEntity.ok(caseworkService.queue(status))

    @PutMapping("/cases/{caseNo}/assignment")
    fun assign(@PathVariable caseNo: Long, @RequestBody request: AssignmentRequest): ResponseEntity<AssignmentResponse> =
        ResponseEntity.ok(caseworkService.assign(caseNo, request))

    @GetMapping("/cases/{caseNo}/assignment")
    fun getAssignment(@PathVariable caseNo: Long): ResponseEntity<AssignmentResponse> =
        ResponseEntity.ok(caseworkService.getAssignment(caseNo))

    @PostMapping("/cases/{caseNo}/rfi")
    fun requestInfo(@PathVariable caseNo: Long, @RequestBody request: RfiRequest): ResponseEntity<RfiResponse> =
        ResponseEntity.ok(caseworkService.requestInfo(caseNo, request))

    @GetMapping("/cases/{caseNo}/documents")
    fun listDocuments(@PathVariable caseNo: Long): ResponseEntity<List<DocumentSummaryResponse>> =
        ResponseEntity.ok(caseworkService.listDocuments(caseNo))

    @PostMapping("/documents/{docId}/review")
    fun review(@PathVariable docId: Long, @RequestBody request: DocumentReviewRequest): ResponseEntity<DocumentReviewResponse> =
        ResponseEntity.ok(caseworkService.reviewDocument(docId, request))
}
