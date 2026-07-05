package com.lakshay.healthcare.ssa

import com.lakshay.healthcare.shared.exception.InvalidSsnException
import com.lakshay.healthcare.ssa.service.SsnValidationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SsnValidationServiceTest {

    private val service = SsnValidationService()

    @Test
    fun `validateSsn maps a valid SSN to its state`() {
        assertEquals("California", service.validateSsn(100000004))
    }

    @Test
    fun `validateSsn throws for an SSN with an unmapped state code`() {
        assertThrows(InvalidSsnException::class.java) {
            service.validateSsn(123456799)
        }
    }
}
