package com.lakshay.healthcare

import com.lakshay.healthcare.application.dto.CitizenRegistrationRequest
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * Citizen application registration (POST /CitizenAR-api/save). The SSN drives the state via
 * SsnValidationService: 9 digits, last two map 1->Washington DC, 2->Ohio, 3->Texas, 4->California,
 * 5->Florida; anything else throws InvalidSsnException -> 400. authenticated() endpoint, bearer is
 * ADMIN here. See docs/TESTING-FLOWS.md.
 */
class CitizenRegistrationIT : IntegrationTestBase() {

    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository

    private fun register(
        ssn: Long,
        fullName: String = "Jane Doe",
        email: String = "citizen@ish.test",
        dob: String? = null
    ): ResultActions = mockMvc.perform(
        post("/CitizenAR-api/save").header(HttpHeaders.AUTHORIZATION, adminAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(CitizenRegistrationRequest(
                fullName, email, gender = "F", phoneNo = 5551234L, ssn = ssn, dob = dob
            )))
    )

    @Test
    fun `CITIZEN-1 registers a citizen and resolves the state from SSN`() {
        register(ssn = 123456704L) // tail 04 -> California
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.appId").isNumber)
            .andExpect(jsonPath("$.stateName").value("California"))

        val saved = citizenRepo.findAll().single()
        assertThat(saved.stateName).isEqualTo("California")
        assertThat(saved.createdBy).isEqualTo("Jane Doe")
    }

    @ParameterizedTest(name = "SSN tail {0} -> {1}")
    @CsvSource(
        "123456701, Washington DC",
        "123456702, Ohio",
        "123456703, Texas",
        "123456704, California",
        "123456705, Florida"
    )
    fun `CITIZEN-1 SSN tail maps to state`(ssn: Long, expectedState: String) {
        register(ssn = ssn)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stateName").value(expectedState))
    }

    @Test
    fun `CITIZEN-1 SSN that is not 9 digits is 400`() {
        register(ssn = 12345L).andExpect(status().isBadRequest)
    }

    @Test
    fun `CITIZEN-1 SSN with unmapped state code is 400`() {
        register(ssn = 123456709L).andExpect(status().isBadRequest) // tail 09 -> no state
    }

    @Test
    fun `CITIZEN-1 parses and stores the date of birth`() {
        register(ssn = 123456704L, dob = "1990-01-01").andExpect(status().isOk)
        assertThat(citizenRepo.findAll().single().dob).isEqualTo(LocalDate.of(1990, 1, 1))
    }
}
