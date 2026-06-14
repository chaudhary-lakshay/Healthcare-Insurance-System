package com.lakshay.healthcare.shared.audit

import com.lakshay.healthcare.shared.entity.AuditEvent
import com.lakshay.healthcare.shared.repository.AuditEventRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class AuditServiceTest {

    private val repo = mock(AuditEventRepository::class.java)
    private val service = AuditService(repo)

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `records actor and role from the security context`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("admin@ish.test", null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))

        service.record("CASE_DETERMINED", "DcCase", "1", "planStatus=APPROVED")

        val captor = ArgumentCaptor.forClass(AuditEvent::class.java)
        verify(repo).save(captor.capture())
        assertEquals("admin@ish.test", captor.value.actor)
        assertEquals("ROLE_ADMIN", captor.value.actorRole)
        assertEquals("CASE_DETERMINED", captor.value.action)
    }

    @Test
    fun `falls back to SYSTEM when there is no security context`() {
        service.record("CASE_DETERMINED", "DcCase", "1", "planStatus=APPROVED")

        val captor = ArgumentCaptor.forClass(AuditEvent::class.java)
        verify(repo).save(captor.capture())
        assertEquals("SYSTEM", captor.value.actor)
    }

    @Test
    fun `detail carries no SSN-shaped value`() {
        service.record("CASE_DETERMINED", "DcCase", "1", "planStatus=APPROVED")

        val captor = ArgumentCaptor.forClass(AuditEvent::class.java)
        verify(repo).save(captor.capture())
        assertFalse(Regex("""\d{3}-?\d{2}-?\d{4}""").containsMatchIn(captor.value.detail ?: ""))
    }
}
