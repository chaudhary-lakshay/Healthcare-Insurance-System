package com.lakshay.healthcare.user.controller

import com.lakshay.healthcare.shared.exception.AccountLockedException
import com.lakshay.healthcare.shared.repository.AdminMasterRepository
import com.lakshay.healthcare.shared.repository.UserMasterRepository
import com.lakshay.healthcare.shared.repository.WorkerMasterRepository
import com.lakshay.healthcare.shared.security.JwtUtil
import com.lakshay.healthcare.shared.security.LoginAttemptService
import com.lakshay.healthcare.shared.security.RefreshTokenService
import com.lakshay.healthcare.user.dto.LoginRequest
import com.lakshay.healthcare.user.dto.RefreshRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userRepository: UserMasterRepository,
    private val workerRepository: WorkerMasterRepository,
    private val adminRepository: AdminMasterRepository,
    private val jwtUtil: JwtUtil,
    private val passwordEncoder: PasswordEncoder,
    private val loginAttemptService: LoginAttemptService,
    private val refreshTokenService: RefreshTokenService
) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Map<String, Any>> {
        // must stay outside the try — the catch below eats everything into a 500
        if (loginAttemptService.isLocked(request.email)) {
            throw AccountLockedException(loginAttemptService.lockoutSeconds())
        }
        return try {
            var name = ""
            var userType = "USER"

            val admin = adminRepository.findByEmail(request.email)
            if (admin != null) {
                if (!passwordEncoder.matches(request.password, admin.password)) {
                    loginAttemptService.recordFailure(request.email)
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(mapOf("error" to "Invalid email or password"))
                }
                name = admin.name
                userType = "ADMIN"
            } else {
                val worker = workerRepository.findByEmail(request.email)
                if (worker != null) {
                    if (!passwordEncoder.matches(request.password, worker.password)) {
                        loginAttemptService.recordFailure(request.email)
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
                        loginAttemptService.recordFailure(request.email)
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(mapOf("error" to "Invalid email or password"))
                    }
                    if (!passwordEncoder.matches(request.password, user.password)) {
                        loginAttemptService.recordFailure(request.email)
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

            loginAttemptService.recordSuccess(request.email)
            val token = jwtUtil.generateToken(request.email, "ROLE_$userType")
            val refreshToken = refreshTokenService.issue(request.email, userType)

            ResponseEntity.ok(
                mapOf(
                    "token" to token,
                    "refreshToken" to refreshToken,
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

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<Map<String, Any>> {
        val rotated = refreshTokenService.rotate(request.refreshToken)
        val access = jwtUtil.generateToken(rotated.email, "ROLE_${rotated.role}")
        return ResponseEntity.ok(
            mapOf(
                "token" to access,
                "refreshToken" to rotated.raw,
                "tokenType" to "Bearer"
            )
        )
    }

    @PostMapping("/logout")
    fun logout(@RequestBody request: RefreshRequest): ResponseEntity<Map<String, String>> {
        refreshTokenService.revoke(request.refreshToken)
        // always 200 — don't confirm whether the token was live
        return ResponseEntity.ok(mapOf("message" to "Logged out"))
    }
}
