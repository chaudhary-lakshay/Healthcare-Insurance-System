package com.lakshay.healthcare

import com.lakshay.healthcare.shared.exception.UnauthorizedException
import com.lakshay.healthcare.shared.security.RefreshTokenService
import com.lakshay.healthcare.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RefreshTokenIT : IntegrationTestBase() {

    @Autowired
    lateinit var refreshTokenService: RefreshTokenService

    private val email = "rt@ish.test"

    private fun seed() = seedUser(email, "pw123456")

    @Test
    fun `rotate returns new token and spends the old one`() {
        seed()
        val first = refreshTokenService.issue(email, "USER")
        val rotated = refreshTokenService.rotate(first)
        assertNotEquals(first, rotated.raw)
        assertEquals(email, rotated.email)
        assertEquals("USER", rotated.role)
        assertThrows<UnauthorizedException> { refreshTokenService.rotate(first) }
    }

    @Test
    fun `reuse of spent token revokes the whole family`() {
        seed()
        val first = refreshTokenService.issue(email, "USER")
        val second = refreshTokenService.rotate(first).raw
        assertThrows<UnauthorizedException> { refreshTokenService.rotate(first) } // reuse -> family burned
        assertThrows<UnauthorizedException> { refreshTokenService.rotate(second) } // sibling dead too
    }

    @Test
    fun `logout revokes the family`() {
        seed()
        val token = refreshTokenService.issue(email, "USER")
        refreshTokenService.revoke(token)
        assertThrows<UnauthorizedException> { refreshTokenService.rotate(token) }
    }

    @Test
    fun `garbage token rejected, live one unaffected`() {
        seed()
        val token = refreshTokenService.issue(email, "USER")
        assertThrows<UnauthorizedException> { refreshTokenService.rotate("not-a-real-token") }
        refreshTokenService.rotate(token) // real one still fine
    }

    @Test
    fun `rotate burns family when the account no longer exists`() {
        val token = refreshTokenService.issue("ghost@ish.test", "USER")
        assertThrows<UnauthorizedException> { refreshTokenService.rotate(token) }
    }
}
