package com.lakshay.healthcare.shared.lifecycle

import com.lakshay.healthcare.shared.entity.DcCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaseStateMachineTest {

    private val sm = CaseStateMachine()

    @Test
    fun `submitted moves to determined`() {
        val moved = sm.transition(DcCase(appId = 1L), CaseStatus.DETERMINED)
        assertEquals(CaseStatus.DETERMINED, moved.caseStatus)
    }

    @Test
    fun `re-determination is idempotent`() {
        assertTrue(sm.canTransition(CaseStatus.DETERMINED, CaseStatus.DETERMINED))
    }

    @Test
    fun `determined cannot go back to submitted`() {
        val determined = DcCase(appId = 1L).copy(caseStatus = CaseStatus.DETERMINED)
        assertThrows(IllegalArgumentException::class.java) {
            sm.transition(determined, CaseStatus.SUBMITTED)
        }
    }

    @Test
    fun `enum name matches the sql default`() {
        assertEquals("SUBMITTED", CaseStatus.SUBMITTED.name)
    }
}
