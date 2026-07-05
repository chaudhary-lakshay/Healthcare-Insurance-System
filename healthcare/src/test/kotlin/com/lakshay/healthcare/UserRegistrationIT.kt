package com.lakshay.healthcare

import com.lakshay.healthcare.support.IntegrationTestBase
import com.lakshay.healthcare.user.dto.ActivateRequest
import com.lakshay.healthcare.user.dto.LoginRequest
import com.lakshay.healthcare.user.dto.RegisterRequest
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * register -> activate -> login for users and workers. The temp password never comes back in the
 * response (only emailed), so we dig it out of the captured MimeMessage. See docs/TESTING-FLOWS.md.
 */
class UserRegistrationIT : IntegrationTestBase() {

    private val tempPwdRegex = Regex("""temporary password is: <b>([A-Za-z0-9]+)</b>""")

    /** Recursively collect text leaves of a (possibly nested) MIME part tree. */
    private fun mimeText(part: Any?): String = when (part) {
        is String -> part
        is MimeMultipart -> (0 until part.count).joinToString("\n") { mimeText(part.getBodyPart(it).content) }
        is jakarta.mail.Part -> mimeText(part.content)
        else -> ""
    }

    /** Pull the generated temp password out of the single registration email. */
    private fun captureTempPassword(): String {
        val captor = ArgumentCaptor.forClass(MimeMessage::class.java)
        verify(mailSender).send(captor.capture())
        // EmailUtils builds a multipart message (MimeMessageHelper(.., multipart=true)); the HTML body
        // is nested a couple of levels down, so walk the tree.
        val body = mimeText(captor.value.content)
        return tempPwdRegex.find(body)?.groupValues?.get(1)
            ?: error("temp password not found in email body:\n$body")
    }

    private fun register(path: String, email: String) = mockMvc.perform(
        post(path).with(servletPath(path)).contentType(MediaType.APPLICATION_JSON).content(
            json(
                RegisterRequest(
                    name = "New Person",
                    password = "ignored-by-service",
                    email = email,
                    gender = "F",
                    designation = "Caseworker"
                )
            )
        )
    )

    @Test
    fun `USER-REG-1 register then activate then login`() {
        val email = "newuser@ish.test"

        // Register: account saved inactive, temp password emailed.
        register("/user-api/save", email)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").isNumber)
        assertThat(userRepository.findByEmail(email)!!.activeSw).isEqualTo("N")

        val tempPwd = captureTempPassword()

        // Activate with temp password + chosen new password.
        mockMvc.perform(
            post("/user-api/activate").with(servletPath("/user-api/activate")).contentType(MediaType.APPLICATION_JSON)
                .content(json(ActivateRequest(email = email, tempPassword = tempPwd, newPassword = "newpass1")))
        ).andExpect(status().isOk)
        assertThat(userRepository.findByEmail(email)!!.activeSw).isEqualTo("Y")

        // Login with the new password.
        mockMvc.perform(
            post("/user-api/login").with(servletPath("/user-api/login")).contentType(MediaType.APPLICATION_JSON)
                .content(json(LoginRequest(email, "newpass1")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ROLE_USER"))
    }

    @Test
    fun `USER-REG-1 duplicate email is 409`() {
        register("/user-api/save", "dup@ish.test").andExpect(status().isOk)
        register("/user-api/save", "dup@ish.test").andExpect(status().isConflict)
    }

    @Test
    fun `USER-REG-1 activate with wrong temp password is 401`() {
        register("/user-api/save", "wrongtmp@ish.test").andExpect(status().isOk)
        mockMvc.perform(
            post("/user-api/activate").with(servletPath("/user-api/activate")).contentType(MediaType.APPLICATION_JSON)
                .content(json(ActivateRequest("wrongtmp@ish.test", "BADPWD", "newpass1")))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `USER-REG-1 activating an already-active account is reported`() {
        seedUser("active@ish.test", "pass123", active = true)
        mockMvc.perform(
            post("/user-api/activate").with(servletPath("/user-api/activate")).contentType(MediaType.APPLICATION_JSON)
                .content(json(ActivateRequest("active@ish.test", "whatever", "newpass1")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("User account is already active"))
    }

    @Test
    fun `WORKER-REG-1 register then activate then login`() {
        val email = "newworker@ish.test"

        register("/worker-api/save", email)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.workerId").isNumber)
        assertThat(workerRepository.findByEmail(email)!!.activeSw).isEqualTo("N")

        val tempPwd = captureTempPassword()

        mockMvc.perform(
            post("/worker-api/activate").with(servletPath("/worker-api/activate")).contentType(MediaType.APPLICATION_JSON)
                .content(json(ActivateRequest(email = email, tempPassword = tempPwd, newPassword = "newpass1")))
        ).andExpect(status().isOk)
        assertThat(workerRepository.findByEmail(email)!!.activeSw).isEqualTo("Y")

        mockMvc.perform(
            post("/worker-api/login").with(servletPath("/worker-api/login")).contentType(MediaType.APPLICATION_JSON)
                .content(json(LoginRequest(email, "newpass1")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ROLE_WORKER"))
    }
}
