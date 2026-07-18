package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*
import java.time.LocalDateTime

// Opaque refresh token. Only the SHA-256 of the raw value is stored — the raw
// token leaves the server exactly once, in the login/refresh response.
@Entity
@Table(name = "REFRESH_TOKEN")
data class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    val tokenId: Long = 0,
    @Column(name = "token_hash")
    val tokenHash: String,
    @Column(name = "family_id")
    val familyId: String,
    @Column(name = "user_email")
    val userEmail: String,
    @Column(name = "role")
    val role: String,
    @Column(name = "expires_at")
    val expiresAt: LocalDateTime,
    @Column(name = "used_sw")
    val usedSw: String = "N",
    @Column(name = "revoked_sw")
    val revokedSw: String = "N",
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
