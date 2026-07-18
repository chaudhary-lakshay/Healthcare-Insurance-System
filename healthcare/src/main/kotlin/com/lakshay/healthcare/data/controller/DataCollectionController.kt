package com.lakshay.healthcare.data.controller

import com.lakshay.healthcare.data.dto.CaseResponse
import com.lakshay.healthcare.data.dto.ChildrenRequest
import com.lakshay.healthcare.data.dto.DcSummaryResponse
import com.lakshay.healthcare.data.dto.EducationRequest
import com.lakshay.healthcare.data.dto.HouseholdMemberRequest
import com.lakshay.healthcare.data.dto.IncomeRequest
import com.lakshay.healthcare.data.dto.PlanNameResponse
import com.lakshay.healthcare.data.dto.PlanSelectionRequest
import com.lakshay.healthcare.data.service.DataCollectionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dc-api")
class DataCollectionController(
    private val dataService: DataCollectionService
) {

    @PostMapping("/loadCaseNo/{appId}")
    fun loadCaseNo(@PathVariable appId: Long): ResponseEntity<CaseResponse> {
        val response = dataService.loadCaseNo(appId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/planNames")
    fun getPlanNames(): ResponseEntity<List<PlanNameResponse>> {
        val plans = dataService.getPlanNames()
        return ResponseEntity.ok(plans)
    }

    @PutMapping("/updatePlanSelection")
    fun updatePlanSelection(@RequestBody request: PlanSelectionRequest): ResponseEntity<Map<String, String>> {
        dataService.updatePlanSelection(request)
        return ResponseEntity.ok(mapOf("message" to "Plan selection updated"))
    }

    @PostMapping("/saveIncome")
    fun saveIncome(@RequestBody request: IncomeRequest): ResponseEntity<Map<String, Any>> {
        val incomeId = dataService.saveIncome(request)
        return ResponseEntity.ok(mapOf("message" to "Income saved", "incomeId" to incomeId))
    }

    @PostMapping("/saveEducation")
    fun saveEducation(@RequestBody request: EducationRequest): ResponseEntity<Map<String, Any>> {
        val educationId = dataService.saveEducation(request)
        return ResponseEntity.ok(mapOf("message" to "Education saved", "educationId" to educationId))
    }

    @PostMapping("/saveChilds")
    fun saveChildren(@RequestBody request: List<ChildrenRequest>): ResponseEntity<Map<String, Any>> {
        val childIds = dataService.saveChildren(request)
        return ResponseEntity.ok(mapOf("message" to "Children saved", "childIds" to childIds))
    }

    @PostMapping("/saveHouseholdMember")
    fun saveHouseholdMember(@RequestBody request: HouseholdMemberRequest): ResponseEntity<Map<String, Any>> {
        val memberId = dataService.saveHouseholdMember(request)
        return ResponseEntity.ok(mapOf("message" to "Household member saved", "memberId" to memberId))
    }

    @GetMapping("/summary/{caseNo}")
    fun getDcSummary(@PathVariable caseNo: Long): ResponseEntity<DcSummaryResponse> {
        val summary = dataService.getDcSummary(caseNo)
        return ResponseEntity.ok(summary)
    }
}
