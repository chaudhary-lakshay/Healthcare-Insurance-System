package com.lakshay.healthcare.shared.notification

import org.junit.jupiter.api.Test
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusEmailTemplateTests {

    private val engine = SpringTemplateEngine().apply {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            templateMode = TemplateMode.HTML
        })
    }

    private fun render(model: Map<String, Any?>): String =
        engine.process("mail/status-change", Context().apply { setVariables(model) })

    private fun baseModel(status: String) = mutableMapOf<String, Any?>(
        "citizenName" to "Jane Doe", "caseNo" to 42L, "status" to status,
        "planName" to null, "benefitAmt" to null, "denialReason" to null, "message" to null
    )

    @Test
    fun `approved shows plan and amount`() {
        val html = render(baseModel("APPROVED").apply { put("planName", "SNAP"); put("benefitAmt", 200.0) })
        assertContains(html, "Jane Doe")
        assertContains(html, "SNAP")
        assertContains(html, "approved")
        assertContains(html, "200.0")
        assertFalse(html.contains("denied"))
    }

    @Test
    fun `approved without benefit amount omits the amount line`() {
        val html = render(baseModel("APPROVED").apply { put("planName", "QHP") })
        assertContains(html, "approved")
        assertFalse(html.contains("Benefit amount"))
    }

    @Test
    fun `denied shows reason`() {
        val html = render(baseModel("DENIED").apply { put("planName", "SNAP"); put("denialReason", "High Income") })
        assertContains(html, "denied")
        assertContains(html, "High Income")
        assertFalse(html.contains("approved"))
    }

    @Test
    fun `rfi shows message`() {
        val html = render(baseModel("RFI").apply { put("message", "Please upload proof of income") })
        assertContains(html, "Please upload proof of income")
        assertContains(html, "portal")
    }

    @Test
    fun `html in model values is escaped`() {
        val html = render(baseModel("DENIED").apply { put("planName", "SNAP"); put("denialReason", "<script>alert(1)</script>") })
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("<script>alert"))
    }
}
