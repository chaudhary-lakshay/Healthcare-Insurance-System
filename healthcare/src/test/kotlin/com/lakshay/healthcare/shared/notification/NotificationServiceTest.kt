package com.lakshay.healthcare.shared.notification

import com.lakshay.healthcare.shared.entity.Notice
import com.lakshay.healthcare.shared.repository.NoticeRepository
import com.lakshay.healthcare.shared.util.EmailUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class NotificationServiceTest {

    private val repo = mock(NoticeRepository::class.java)
    private val email = mock(EmailUtils::class.java)
    private val service = NotificationService(repo, email)

    @Test
    fun `portal notice is written as SENT and sends no email`() {
        `when`(repo.save(any(Notice::class.java))).thenAnswer { it.arguments[0] }

        service.notifyPortal(1L, "jane@ish.test", "PLAN_DECISION", "subj", "body")

        val captor = ArgumentCaptor.forClass(Notice::class.java)
        verify(repo).save(captor.capture())
        assertEquals("PORTAL", captor.value.channel)
        assertEquals("SENT", captor.value.status)
        verifyNoInteractions(email)
    }

    @Test
    fun `email notice records SENT when the mail goes out`() {
        `when`(repo.save(any(Notice::class.java))).thenAnswer { it.arguments[0] }
        `when`(email.sendEmail(anyString(), anyString(), anyString())).thenReturn(true)

        service.notifyEmail(1L, "jane@ish.test", "PLAN_DECISION", "subj", "body")

        verify(email).sendEmail("subj", "body", "jane@ish.test")
        val captor = ArgumentCaptor.forClass(Notice::class.java)
        verify(repo, times(2)).save(captor.capture())
        assertEquals("SENT", captor.allValues.last().status)
    }

    @Test
    fun `email notice records FAILED when the mail fails`() {
        `when`(repo.save(any(Notice::class.java))).thenAnswer { it.arguments[0] }
        `when`(email.sendEmail(anyString(), anyString(), anyString())).thenReturn(false)

        service.notifyEmail(1L, "jane@ish.test", "PLAN_DECISION", "subj", "body")

        val captor = ArgumentCaptor.forClass(Notice::class.java)
        verify(repo, times(2)).save(captor.capture())
        assertEquals("FAILED", captor.allValues.last().status)
    }
}
