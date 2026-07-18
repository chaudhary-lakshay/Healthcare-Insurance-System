package com.lakshay.healthcare.shared.security

import io.jsonwebtoken.ExpiredJwtException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtUtilTests {

    private val util = JwtUtil(
        secret = "test-only-jwt-secret-not-for-production-use-min-256-bits-0123456789",
        expirationMs = 1_800_000L,
        issuer = "ish-health-system",
        audience = "ish-services"
    )

    @Test
    fun `round trip after jjwt 0_13 migration`() {
        val token = util.generateToken("a@b.com", "ROLE_ADMIN")
        assertEquals("a@b.com", util.extractEmail(token))
        assertEquals("ROLE_ADMIN", util.extractRole(token))
        assertFalse(util.isTokenExpired(token))
        assertTrue(util.validateToken(token, "a@b.com"))
        assertFalse(util.validateToken(token, "someone@else.com"))
    }

    @Test
    fun `expired token throws at parse`() {
        // jjwt refuses to even parse an expired token; JwtAuthFilter catches this
        // and leaves the request unauthenticated
        val shortLived = JwtUtil(
            secret = "test-only-jwt-secret-not-for-production-use-min-256-bits-0123456789",
            expirationMs = -1000L,
            issuer = "ish-health-system",
            audience = "ish-services"
        )
        val token = shortLived.generateToken("a@b.com", "ROLE_USER")
        assertThrows<ExpiredJwtException> { shortLived.isTokenExpired(token) }
    }
}
