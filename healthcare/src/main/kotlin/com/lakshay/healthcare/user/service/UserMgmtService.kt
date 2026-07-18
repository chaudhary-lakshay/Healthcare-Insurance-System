package com.lakshay.healthcare.user.service

import com.lakshay.healthcare.admin.dto.UserDataResponse
import com.lakshay.healthcare.shared.entity.UserMaster
import com.lakshay.healthcare.shared.repository.UserMasterRepository
import com.lakshay.healthcare.shared.security.JwtUtil
import com.lakshay.healthcare.shared.util.EmailUtils
import com.lakshay.healthcare.user.dto.ActivateRequest
import com.lakshay.healthcare.user.dto.LoginRequest
import com.lakshay.healthcare.user.dto.LoginResponse
import com.lakshay.healthcare.user.dto.RegisterRequest
import com.lakshay.healthcare.shared.exception.DuplicateResourceException
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.exception.AccountLockedException
import com.lakshay.healthcare.shared.exception.UnauthorizedException
import com.lakshay.healthcare.shared.security.LoginAttemptService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime

data class RegistrationResult(
    val id: Long,
    val tempPassword: String
)

@Service
class UserMgmtService(
    private val userRepository: UserMasterRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val emailUtils: EmailUtils,
    private val loginAttemptService: LoginAttemptService
) {

    fun registerUser(request: RegisterRequest): RegistrationResult = register(request, "USER")

    fun registerCitizen(request: RegisterRequest): RegistrationResult = register(request, "CITIZEN")

    private fun register(request: RegisterRequest, role: String): RegistrationResult {
        if (userRepository.findByEmail(request.email) != null) {
            throw DuplicateResourceException("User with email ${request.email} already exists")
        }

        val tempPwd = generateRandomPassword(6)

        val user = UserMaster(
            name = request.name,
            password = passwordEncoder.encode(tempPwd),
            email = request.email,
            mobileNo = request.mobileNo,
            ssn = request.ssn,
            gender = request.gender,
            dob = request.dob?.let { LocalDate.parse(it) },
            activeSw = "N",
            role = role,
            createdBy = request.name,
            updatedBy = request.name
        )

        val saved = userRepository.save(user)

        val mailBody = buildRegistrationEmail(saved.userId, tempPwd)
        emailUtils.sendEmail("Account Activation - ISH", mailBody, saved.email)

        return RegistrationResult(id = saved.userId, tempPassword = tempPwd)
    }

    fun activateUser(request: ActivateRequest): String {
        val user = userRepository.findByEmail(request.email)
            ?: throw ResourceNotFoundException("User not found with email: ${request.email}")

        if (user.activeSw == "Y") {
            return "User account is already active"
        }

        if (!passwordEncoder.matches(request.tempPassword, user.password)) {
            throw UnauthorizedException("Invalid temporary password")
        }

        val updated = user.copy(
            password = passwordEncoder.encode(request.newPassword),
            activeSw = "Y",
            updatedOn = LocalDateTime.now(),
            updatedBy = user.name
        )
        userRepository.save(updated)
        return "User activated successfully"
    }

    fun loginUser(request: LoginRequest): LoginResponse {
        if (loginAttemptService.isLocked(request.email)) {
            throw AccountLockedException(loginAttemptService.lockoutSeconds())
        }

        val user = userRepository.findByEmail(request.email)
            ?: run {
                loginAttemptService.recordFailure(request.email)
                throw UnauthorizedException("Invalid email or password")
            }

        if (!passwordEncoder.matches(request.password, user.password)) {
            loginAttemptService.recordFailure(request.email)
            throw UnauthorizedException("Invalid email or password")
        }

        if (user.activeSw != "Y") {
            throw UnauthorizedException("Account not activated. Please activate your account first.")
        }

        loginAttemptService.recordSuccess(request.email)
        val token = jwtUtil.generateToken(user.email, "ROLE_${user.role}")
        return LoginResponse(
            token = token,
            role = "ROLE_${user.role}",
            userId = user.userId
        )
    }

    fun listUsers(): List<UserDataResponse> {
        return userRepository.findAll().map { entity ->
            UserDataResponse(
                userId = entity.userId,
                name = entity.name,
                email = entity.email,
                mobileNo = entity.mobileNo,
                ssn = entity.ssn,
                gender = entity.gender,
                activeSw = entity.activeSw,
                role = entity.role
            )
        }
    }

    fun showUserByUserId(id: Long): UserDataResponse {
        val user = userRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("User not found: $id") }
        return UserDataResponse(
            userId = user.userId,
            name = user.name,
            email = user.email,
            mobileNo = user.mobileNo,
            ssn = user.ssn,
            gender = user.gender,
            activeSw = user.activeSw,
            role = user.role
        )
    }

    fun updateUser(request: UserDataResponse): String {
        val user = userRepository.findById(request.userId)
            .orElseThrow { ResourceNotFoundException("User not found: ${request.userId}") }
        val updated = user.copy(
            name = request.name,
            mobileNo = request.mobileNo,
            gender = request.gender,
            updatedOn = LocalDateTime.now(),
            updatedBy = "ADMIN"
        )
        userRepository.save(updated)
        return "User ${request.userId} updated successfully"
    }

    fun deleteUserById(id: Long): String {
        val user = userRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("User not found: $id") }
        userRepository.delete(user)
        return "User $id deleted successfully"
    }

    fun changeUserStatus(id: Long, status: String): String {
        val user = userRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("User not found: $id") }
        val updated = user.copy(activeSw = status, updatedOn = LocalDateTime.now(), updatedBy = "ADMIN")
        userRepository.save(updated)
        return "User $id status changed to $status"
    }

    private fun generateRandomPassword(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    private fun buildRegistrationEmail(userId: Long, tempPwd: String): String {
        return """
            <h2>Welcome to Insurance System for Health</h2>
            <p>Your registration was successful.</p>
            <p>User ID: ${userId}</p>
            <p>Your temporary password is: <b>${tempPwd}</b></p>
            <p>Please use your registered email and this temporary password to activate your account.</p>
            <p>Activation URL: http://localhost:8080/user-api/activate</p>
            <p>Provide your email, temporary password, and a new password of your choice to activate.</p>
        """.trimIndent()
    }
}
