package com.lakshay.healthcare.casework.controller

import com.lakshay.healthcare.casework.dto.CaseNoteRequest
import com.lakshay.healthcare.casework.dto.CaseNoteResponse
import com.lakshay.healthcare.casework.dto.QueueItemResponse
import com.lakshay.healthcare.casework.service.CaseworkService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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
}
