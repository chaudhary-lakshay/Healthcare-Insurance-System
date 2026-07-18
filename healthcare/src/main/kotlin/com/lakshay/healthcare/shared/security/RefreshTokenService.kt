package com.lakshay.healthcare.shared.security

import com.lakshay.healthcare.shared.entity.RefreshToken
import com.lakshay.healthcare.shared.exception.UnauthorizedException
import com.lakshay.healthcare.shared.repository.AdminMasterRepository
import com.lakshay.healthcare.shared.repository.RefreshTokenRepository
import com.lakshay.healthcare.shared.repository.UserMasterRepository
import com.lakshay.healthcare.shared.repository.WorkerMasterRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

// Rotating refresh tokens: single-use, new pair per exchange, whole family dies
// on reuse (stolen-token detection). Roles are re-read from the account tables at
// rotate time so a demotion doesn't ride a 14-day-old snapshot.
@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val adminRepository: AdminMasterRepository,
    private val workerRepository: WorkerMasterRepository,
    private val userRepository: UserMasterRepository,
    @Value("\${jwt.refresh-expiration}") private val refreshExpirationMs: Long
) {
    private val log = LoggerFactory.getLogger(RefreshTokenService::class.java)
    private val random = SecureRandom()

    data class RotatedToken(val raw: String, val email: String, val role: String)

    @Transactional
    fun issue(email: String, role: String): String {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now()) // lazy purge, no scheduler
        return newToken(email, role, UUID.randomUUID().toString())
    }

    // noRollbackFor: the 401 must NOT undo the family revocation written in this
    // same tx — otherwise reuse detection rolls itself back
    @Transactional(noRollbackFor = [UnauthorizedException::class])
    fun rotate(rawToken: String): RotatedToken {
        val hash = sha256(rawToken)
        if (refreshTokenRepository.claim(hash) == 0) {
            // spent, revoked, or garbage. If the row exists this is token reuse — burn the family.
            refreshTokenRepository.findByTokenHash(hash)?.let {
                refreshTokenRepository.revokeFamily(it.familyId)
                log.warn("Refresh token reuse for {} — family {} revoked", it.userEmail, it.familyId)
            }
            throw UnauthorizedException("Invalid refresh token")
        }

        val row = refreshTokenRepository.findByTokenHash(hash)
            ?: throw UnauthorizedException("Invalid refresh token")

        if (LocalDateTime.now().isAfter(row.expiresAt)) {
            throw UnauthorizedException("Invalid refresh token")
        }

        val role = currentRole(row.userEmail) ?: run {
            refreshTokenRepository.revokeFamily(row.familyId) // account gone
            throw UnauthorizedException("Invalid refresh token")
        }

        return RotatedToken(newToken(row.userEmail, role, row.familyId), row.userEmail, role)
    }

    // logout. Silent on unknown tokens — response must not confirm validity.
    @Transactional
    fun revoke(rawToken: String) {
        refreshTokenRepository.findByTokenHash(sha256(rawToken))?.let {
            refreshTokenRepository.revokeFamily(it.familyId)
        }
    }

    private fun newToken(email: String, role: String, familyId: String): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        refreshTokenRepository.save(
            RefreshToken(
                tokenHash = sha256(raw),
                familyId = familyId,
                userEmail = email,
                role = role,
                expiresAt = LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000)
            )
        )
        return raw
    }

    private fun currentRole(email: String): String? =
        adminRepository.findByEmail(email)?.role
            ?: workerRepository.findByEmail(email)?.role
            ?: userRepository.findByEmail(email)?.role

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
