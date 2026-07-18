package com.lakshay.healthcare.shared.security

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RateLimitingTests {

    @Test
    fun `locks account after max failures`() {
        val svc = LoginAttemptService(maxFailures = 3, lockoutMinutes = 15L)
        repeat(2) { svc.recordFailure("a@b.com") }
        assertFalse(svc.isLocked("a@b.com"))
        svc.recordFailure("a@b.com")
        assertTrue(svc.isLocked("a@b.com"))
        assertFalse(svc.isLocked("other@b.com"))
    }

    @Test
    fun `success resets failure count`() {
        val svc = LoginAttemptService(maxFailures = 3, lockoutMinutes = 15L)
        repeat(2) { svc.recordFailure("a@b.com") }
        svc.recordSuccess("a@b.com")
        repeat(2) { svc.recordFailure("a@b.com") }
        assertFalse(svc.isLocked("a@b.com"))
    }

    @Test
    fun `tracks unknown emails too`() {
        val svc = LoginAttemptService(maxFailures = 1, lockoutMinutes = 15L)
        svc.recordFailure("ghost@nowhere.com")
        assertTrue(svc.isLocked("ghost@nowhere.com"))
    }

    @Test
    fun `ip bucket returns 429 with Retry-After then refills`() {
        val filter = RateLimitFilter(limit = 2, window = Duration.ofSeconds(1))

        fun fire(): MockHttpServletResponse {
            val req = MockHttpServletRequest("POST", "/api/auth/login")
            val res = MockHttpServletResponse()
            filter.doFilter(req, res, MockFilterChain())
            return res
        }

        assertEquals(HttpStatus.OK.value(), fire().status)
        assertEquals(HttpStatus.OK.value(), fire().status)
        val limited = fire()
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), limited.status)
        assertNotNull(limited.getHeader("Retry-After"))

        Thread.sleep(1200) // window refill
        assertEquals(HttpStatus.OK.value(), fire().status)
    }

    @Test
    fun `non-auth paths skip the filter`() {
        val filter = RateLimitFilter(limit = 1, window = Duration.ofMinutes(15))
        repeat(3) {
            val req = MockHttpServletRequest("GET", "/plan-api/categories")
            val res = MockHttpServletResponse()
            filter.doFilter(req, res, MockFilterChain())
            assertEquals(HttpStatus.OK.value(), res.status)
        }
    }
}
