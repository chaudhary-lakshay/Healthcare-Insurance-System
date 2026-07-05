package com.lakshay.healthcare.user.controller

import com.lakshay.healthcare.user.dto.LoginRequest
import com.lakshay.healthcare.user.dto.LoginResponse
import com.lakshay.healthcare.user.service.UserMgmtService
import com.lakshay.healthcare.user.service.WorkerMgmtService
import com.lakshay.healthcare.shared.entity.AdminMaster
import com.lakshay.healthcare.shared.exception.UnauthorizedException
import com.lakshay.healthcare.shared.repository.AdminMasterRepository
import com.lakshay.healthcare.shared.repository.UserMasterRepository
import com.lakshay.healthcare.shared.repository.WorkerMasterRepository
import com.lakshay.healthcare.shared.security.JwtUtil
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userRepository: UserMasterRepository,
    private val workerRepository: WorkerMasterRepository,
    private val adminRepository: AdminMasterRepository,
    private val userService: UserMgmtService,
    private val workerService: WorkerMgmtService,
    private val jwtUtil: JwtUtil,
    private val passwordEncoder: PasswordEncoder
) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Map<String, Any>> {
        return try {
            var name = ""
            var userType = "USER"

            val admin = adminRepository.findByEmail(request.email)
            if (admin != null) {
                if (!passwordEncoder.matches(request.password, admin.password)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(mapOf("error" to "Invalid email or password"))
                }
                name = admin.name
                userType = "ADMIN"
            } else {
                val worker = workerRepository.findByEmail(request.email)
                if (worker != null) {
                    if (!passwordEncoder.matches(request.password, worker.password)) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(mapOf("error" to "Invalid email or password"))
                    }
                    if (worker.activeSw != "Y") {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(mapOf("error" to "Account not activated. Please activate your account first."))
                    }
                    name = worker.name
                    userType = "WORKER"
                } else {
                    val user = userRepository.findByEmail(request.email)
                    if (user == null) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(mapOf("error" to "Invalid email or password"))
                    }
                    if (!passwordEncoder.matches(request.password, user.password)) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(mapOf("error" to "Invalid email or password"))
                    }
                    if (user.activeSw != "Y") {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(mapOf("error" to "Account not activated. Please activate your account first."))
                    }
                    name = user.name
                    userType = "USER"
                }
            }

            val token = jwtUtil.generateToken(request.email, "ROLE_$userType")

            ResponseEntity.ok(
                mapOf(
                    "token" to token,
                    "tokenType" to "Bearer",
                    "email" to request.email,
                    "name" to name,
                    "type" to userType,
                    "message" to "Login successful"
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An error occurred during authentication"))
        }
    }
}
