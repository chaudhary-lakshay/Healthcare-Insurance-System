package com.lakshay.healthcare.user.service

import com.lakshay.healthcare.admin.dto.WorkerDataResponse
import com.lakshay.healthcare.shared.entity.WorkerMaster
import com.lakshay.healthcare.shared.repository.WorkerMasterRepository
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
import com.lakshay.healthcare.shared.security.RefreshTokenService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime

data class WorkerRegistrationResult(
    val id: Long,
    val tempPassword: String
)

@Service
class WorkerMgmtService(
    private val workerRepository: WorkerMasterRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val emailUtils: EmailUtils,
    private val loginAttemptService: LoginAttemptService,
    private val refreshTokenService: RefreshTokenService
) {

    companion object {
        private const val PASSWORD_LENGTH = 6
    }

    fun registerWorker(request: RegisterRequest): WorkerRegistrationResult {
        if (workerRepository.findByEmail(request.email) != null) {
            throw DuplicateResourceException("Worker with email ${request.email} already exists")
        }

        val tempPwd = generateRandomPassword(PASSWORD_LENGTH)

        val worker = WorkerMaster(
            name = request.name,
            password = passwordEncoder.encode(tempPwd),
            email = request.email,
            mobileNo = request.mobileNo,
            ssn = request.ssn,
            gender = request.gender,
            dob = request.dob?.let { LocalDate.parse(it) },
            designation = request.designation,
            helpCenterName = request.helpCenterName,
            helpCenterLocation = request.helpCenterLocation,
            activeSw = "N",
            role = "WORKER",
            createdBy = request.name,
            updatedBy = request.name
        )

        val saved = workerRepository.save(worker)

        val mailBody = buildRegistrationEmail(saved.workerId, tempPwd)
        emailUtils.sendEmail("Worker Account Activation - ISH", mailBody, saved.email)

        return WorkerRegistrationResult(id = saved.workerId, tempPassword = tempPwd)
    }

    fun activateWorker(request: ActivateRequest): String {
        val worker = workerRepository.findByEmail(request.email)
            ?: throw ResourceNotFoundException("Worker not found with email: ${request.email}")

        if (worker.activeSw == "Y") {
            return "Worker account is already active"
        }

        if (!passwordEncoder.matches(request.tempPassword, worker.password)) {
            throw UnauthorizedException("Invalid temporary password")
        }

        val updated = worker.copy(
            password = passwordEncoder.encode(request.newPassword),
            activeSw = "Y",
            updatedOn = LocalDateTime.now(),
            updatedBy = worker.name
        )
        workerRepository.save(updated)
        return "Worker activated successfully"
    }

    // ThrowsCount: distinct auth failures (locked / unknown / bad password / not activated),
    // each its own status; folding would blur the 401 reasons.
    @Suppress("ThrowsCount")
    fun loginWorker(request: LoginRequest): LoginResponse {
        if (loginAttemptService.isLocked(request.email)) {
            throw AccountLockedException(loginAttemptService.lockoutSeconds())
        }

        val worker = workerRepository.findByEmail(request.email)
            ?: run {
                loginAttemptService.recordFailure(request.email)
                throw UnauthorizedException("Invalid email or password")
            }

        if (!passwordEncoder.matches(request.password, worker.password)) {
            loginAttemptService.recordFailure(request.email)
            throw UnauthorizedException("Invalid email or password")
        }

        if (worker.activeSw != "Y") {
            throw UnauthorizedException("Account not activated. Please activate your account first.")
        }

        loginAttemptService.recordSuccess(request.email)
        val token = jwtUtil.generateToken(worker.email, "ROLE_${worker.role}")
        return LoginResponse(
            token = token,
            role = "ROLE_${worker.role}",
            workerId = worker.workerId,
            refreshToken = refreshTokenService.issue(worker.email, worker.role)
        )
    }

    fun listWorkers(): List<WorkerDataResponse> {
        return workerRepository.findAll().map { entity ->
            WorkerDataResponse(
                workerId = entity.workerId,
                name = entity.name,
                email = entity.email,
                mobileNo = entity.mobileNo,
                ssn = entity.ssn,
                gender = entity.gender,
                designation = entity.designation,
                activeSw = entity.activeSw,
                role = entity.role
            )
        }
    }

    fun showWorkerByWorkerId(id: Long): WorkerDataResponse {
        val worker = workerRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Worker not found: $id") }
        return WorkerDataResponse(
            workerId = worker.workerId,
            name = worker.name,
            email = worker.email,
            mobileNo = worker.mobileNo,
            ssn = worker.ssn,
            gender = worker.gender,
            designation = worker.designation,
            activeSw = worker.activeSw,
            role = worker.role
        )
    }

    fun updateWorker(request: WorkerDataResponse): String {
        val worker = workerRepository.findById(request.workerId)
            .orElseThrow { ResourceNotFoundException("Worker not found: ${request.workerId}") }
        val updated = worker.copy(
            name = request.name,
            mobileNo = request.mobileNo,
            gender = request.gender,
            designation = request.designation ?: worker.designation,
            updatedOn = LocalDateTime.now(),
            updatedBy = "ADMIN"
        )
        workerRepository.save(updated)
        return "Worker ${request.workerId} updated successfully"
    }

    fun deleteWorkerById(id: Long): String {
        val worker = workerRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Worker not found: $id") }
        workerRepository.delete(worker)
        return "Worker $id deleted successfully"
    }

    fun changeWorkerStatus(id: Long, status: String): String {
        val worker = workerRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Worker not found: $id") }
        val updated = worker.copy(activeSw = status, updatedOn = LocalDateTime.now(), updatedBy = "ADMIN")
        workerRepository.save(updated)
        return "Worker $id status changed to $status"
    }

    private fun generateRandomPassword(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    private fun buildRegistrationEmail(workerId: Long, tempPwd: String): String {
        return """
            <h2>Welcome to Insurance System for Health</h2>
            <p>Your worker registration was successful.</p>
            <p>Worker ID: ${workerId}</p>
            <p>Your temporary password is: <b>${tempPwd}</b></p>
            <p>Please use your registered email and this temporary password to activate your account.</p>
            <p>Activation URL: http://localhost:8080/worker-api/activate</p>
            <p>Provide your email, temporary password, and a new password of your choice to activate.</p>
        """.trimIndent()
    }
}
