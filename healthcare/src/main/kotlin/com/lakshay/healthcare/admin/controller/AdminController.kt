package com.lakshay.healthcare.admin.controller

import com.lakshay.healthcare.admin.dto.PlanCategoryResponse
import com.lakshay.healthcare.admin.dto.PlanDataResponse
import com.lakshay.healthcare.admin.dto.PlanRequest
import com.lakshay.healthcare.admin.service.PlanManagementService
import com.lakshay.healthcare.shared.entity.AdminMaster
import com.lakshay.healthcare.shared.repository.AdminMasterRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/plan-api")
class AdminController(
    private val planService: PlanManagementService,
    private val adminRepository: AdminMasterRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @GetMapping("/categories")
    fun getCategories(): ResponseEntity<List<PlanCategoryResponse>> {
        val categories = planService.getCategories()
        return ResponseEntity.ok(categories)
    }

    @PostMapping("/register")
    fun registerPlan(@RequestBody request: PlanRequest): ResponseEntity<Map<String, Any>> {
        val response = planService.registerPlan(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapOf("message" to response, "planId" to extractPlanId(response)))
    }

    @GetMapping("/all")
    fun getAllPlans(): ResponseEntity<List<PlanDataResponse>> {
        val plans = planService.showAllPlans()
        return ResponseEntity.ok(plans)
    }

    @GetMapping("/find/{planId}")
    fun getPlanById(@PathVariable planId: Long): ResponseEntity<PlanDataResponse> {
        val plan = planService.showPlanById(planId)
        return ResponseEntity.ok(plan)
    }

    @PutMapping("/update")
    fun updatePlan(@RequestBody request: PlanDataResponse): ResponseEntity<Map<String, String>> {
        val message = planService.updatePlan(request)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @DeleteMapping("/delete/{planId}")
    fun deletePlan(@PathVariable planId: Long): ResponseEntity<Map<String, String>> {
        val message = planService.deletePlan(planId)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PutMapping("/status-change/{planId}/{status}")
    fun changePlanStatus(
        @PathVariable planId: Long,
        @PathVariable status: String
    ): ResponseEntity<Map<String, String>> {
        val message = planService.changePlanStatus(planId, status)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    private fun extractPlanId(response: String): String {
        return response.split(" ").lastOrNull() ?: ""
    }
}

@RestController
@RequestMapping("/admin-api")
class AdminUserController(
    private val adminRepository: AdminMasterRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @PostMapping("/create")
    fun createAdmin(@RequestBody request: Map<String, String>): ResponseEntity<Map<String, Any>> {
        val email = request["email"]
        val name = request["name"]
        val password = request["password"]

        // single exit for all validation — detekt caps returns at 2
        val error = when {
            email == null -> HttpStatus.BAD_REQUEST to "Email is required"
            name == null -> HttpStatus.BAD_REQUEST to "Name is required"
            password == null -> HttpStatus.BAD_REQUEST to "Password is required"
            adminRepository.findByEmail(email) != null -> HttpStatus.CONFLICT to "Admin with this email already exists"
            else -> null
        }
        if (error != null) {
            return ResponseEntity.status(error.first).body(mapOf("error" to error.second))
        }

        val admin = AdminMaster(
            name = name!!,
            email = email!!,
            password = passwordEncoder.encode(password!!),
            role = "ADMIN",
            activeSw = "Y"
        )
        val saved = adminRepository.save(admin)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "adminId" to saved.adminId,
                "name" to saved.name,
                "email" to saved.email,
                "role" to saved.role,
                "message" to "Admin created successfully"
            )
        )
    }
}
