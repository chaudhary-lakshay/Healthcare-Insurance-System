package com.lakshay.healthcare.correspondence.service

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RenewalReminderTests {

    private val today = LocalDate.of(2026, 1, 1)

    @Test
    fun `hits the 60 30 7 day marks`() {
        assertEquals(60L, RenewalReminderService.dueMilestone(today, today.plusDays(60)))
        assertEquals(30L, RenewalReminderService.dueMilestone(today, today.plusDays(30)))
        assertEquals(7L, RenewalReminderService.dueMilestone(today, today.plusDays(7)))
    }

    @Test
    fun `dates between the marks are not due`() {
        assertNull(RenewalReminderService.dueMilestone(today, today.plusDays(45)))
        assertNull(RenewalReminderService.dueMilestone(today, today.plusDays(15)))
        assertNull(RenewalReminderService.dueMilestone(today, today.plusDays(1)))
    }

    @Test
    fun `past end date is not due`() {
        assertNull(RenewalReminderService.dueMilestone(today, today.minusDays(10)))
    }

    @Test
    fun `null end date is not due`() {
        assertNull(RenewalReminderService.dueMilestone(today, null))
    }
}
