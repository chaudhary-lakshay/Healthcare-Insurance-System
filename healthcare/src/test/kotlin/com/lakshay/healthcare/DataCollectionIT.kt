package com.lakshay.healthcare

import com.lakshay.healthcare.data.dto.ChildrenRequest
import com.lakshay.healthcare.data.dto.EducationRequest
import com.lakshay.healthcare.data.dto.IncomeRequest
import com.lakshay.healthcare.data.dto.PlanSelectionRequest
import com.lakshay.healthcare.shared.entity.CitizenAppRegistration
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.entity.DcChildren
import com.lakshay.healthcare.shared.entity.DcEducation
import com.lakshay.healthcare.shared.entity.DcIncome
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.DcChildrenRepository
import com.lakshay.healthcare.shared.repository.DcEducationRepository
import com.lakshay.healthcare.shared.repository.DcIncomeRepository
import com.lakshay.healthcare.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * Data-collection ops under /dc-api (MockMvc + JWT). These are only authenticated() in
 * SecurityConfig — any logged-in role works, so requests carry an ADMIN bearer just to clear the
 * filter. See docs/TESTING-FLOWS.md.
 */
class DataCollectionIT : IntegrationTestBase() {

    @Autowired private lateinit var citizenRepo: CitizenAppRegistrationRepository

    @Autowired private lateinit var dcCaseRepo: DcCaseRepository

    @Autowired private lateinit var dcIncomeRepo: DcIncomeRepository

    @Autowired private lateinit var dcEducationRepo: DcEducationRepository

    @Autowired private lateinit var dcChildrenRepo: DcChildrenRepository

    private fun seedCitizen(
        ssn: Long = 123456704L,
        fullName: String = "Jane Doe"
    ): Long = citizenRepo.save(
        CitizenAppRegistration(
            fullName = fullName,
            email = "citizen@ish.test",
            gender = "F",
            ssn = ssn,
            stateName = "California",
            dob = LocalDate.of(1990, 1, 1)
        )
    ).appId

    // loadCaseNo

    @Test
    fun `DATA-1 loadCaseNo creates a case for a citizen application`() {
        val appId = seedCitizen()
        mockMvc.perform(
            post("/dc-api/loadCaseNo/$appId").header(HttpHeaders.AUTHORIZATION, adminAuth())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.caseNo").isNumber)

        assertThat(dcCaseRepo.findByAppId(appId)).isNotNull
    }

    @Test
    fun `DATA-1 loadCaseNo unknown appId is 404`() {
        mockMvc.perform(
            post("/dc-api/loadCaseNo/999999").header(HttpHeaders.AUTHORIZATION, adminAuth())
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `DATA-1 loadCaseNo twice for same app is 409`() {
        // duplicate guard throws DuplicateResourceException -> 409
        val appId = seedCitizen()
        mockMvc.perform(post("/dc-api/loadCaseNo/$appId").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
        mockMvc.perform(post("/dc-api/loadCaseNo/$appId").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isConflict)
    }

    // getPlanNames

    @Test
    fun `DATA-1 planNames returns the seeded plans`() {
        mockMvc.perform(get("/dc-api/planNames").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(SEED_PLANS.size))
            .andExpect(jsonPath("$[*].planName", org.hamcrest.Matchers.hasItem("SNAP")))
    }

    // updatePlanSelection

    @Test
    fun `DATA-1 updatePlanSelection sets the plan on the case`() {
        val appId = seedCitizen()
        val caseNo = dcCaseRepo.save(DcCase(appId = appId)).caseNo
        val planId = planId("SNAP")

        mockMvc.perform(
            put("/dc-api/updatePlanSelection").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(PlanSelectionRequest(caseNo = caseNo, planId = planId)))
        ).andExpect(status().isOk)

        assertThat(dcCaseRepo.findByCaseNo(caseNo)!!.planId).isEqualTo(planId)
    }

    @Test
    fun `DATA-1 updatePlanSelection unknown case is 404`() {
        mockMvc.perform(
            put("/dc-api/updatePlanSelection").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(PlanSelectionRequest(caseNo = 999999, planId = planId("SNAP"))))
        ).andExpect(status().isNotFound)
    }

    // saveIncome / saveEducation / saveChilds

    @Test
    fun `DATA-1 saveIncome persists the income row`() {
        val caseNo = dcCaseRepo.save(DcCase(appId = seedCitizen())).caseNo
        mockMvc.perform(
            post("/dc-api/saveIncome").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(IncomeRequest(caseNo = caseNo, empIncome = 100.0, propertyIncome = 0.0)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.incomeId").isNumber)

        assertThat(dcIncomeRepo.findByCaseNo(caseNo)!!.empIncome).isEqualTo(100.0)
    }

    @Test
    fun `DATA-1 saveEducation persists the education row`() {
        val caseNo = dcCaseRepo.save(DcCase(appId = seedCitizen())).caseNo
        mockMvc.perform(
            post("/dc-api/saveEducation").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(EducationRequest(caseNo = caseNo, highestQlfy = "Bachelors", passOutYear = 2018)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.educationId").isNumber)

        assertThat(dcEducationRepo.findByCaseNo(caseNo)!!.passOutYear).isEqualTo(2018)
    }

    @Test
    fun `DATA-1 saveChilds persists each child and returns ids`() {
        val caseNo = dcCaseRepo.save(DcCase(appId = seedCitizen())).caseNo
        mockMvc.perform(
            post("/dc-api/saveChilds").header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        listOf(
                            ChildrenRequest(caseNo = caseNo, childDOB = "2015-01-01", childSSN = 111L),
                            ChildrenRequest(caseNo = caseNo, childDOB = "2018-06-15", childSSN = 222L)
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.childIds.length()").value(2))

        assertThat(dcChildrenRepo.findByCaseNo(caseNo)).hasSize(2)
    }

    // getDcSummary

    @Test
    fun `DATA-1 summary assembles citizen plan income education and children`() {
        val appId = seedCitizen(ssn = 123456704L, fullName = "Jane Doe")
        val caseNo = dcCaseRepo.save(DcCase(appId = appId, planId = planId("SNAP"))).caseNo
        dcIncomeRepo.save(DcIncome(caseNo = caseNo, empIncome = 100.0, propertyIncome = 0.0))
        dcEducationRepo.save(DcEducation(caseNo = caseNo, highestQlfy = "Bachelors", passOutYear = 2018))
        dcChildrenRepo.save(DcChildren(caseNo = caseNo, childDOB = LocalDate.of(2015, 1, 1), childSSN = 111L))

        mockMvc.perform(get("/dc-api/summary/$caseNo").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.caseNo").value(caseNo))
            .andExpect(jsonPath("$.planName").value("SNAP"))
            .andExpect(jsonPath("$.citizenName").value("Jane Doe"))
            .andExpect(jsonPath("$.citizenSsn").value(123456704L))
            .andExpect(jsonPath("$.income.empIncome").value(100.0))
            .andExpect(jsonPath("$.education.passOutYear").value(2018))
            .andExpect(jsonPath("$.children.length()").value(1))
    }

    @Test
    fun `DATA-1 summary unknown case is 404`() {
        mockMvc.perform(get("/dc-api/summary/999999").header(HttpHeaders.AUTHORIZATION, adminAuth()))
            .andExpect(status().isNotFound)
    }
}
