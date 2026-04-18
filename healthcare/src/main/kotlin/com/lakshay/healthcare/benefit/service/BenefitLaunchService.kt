package com.lakshay.healthcare.benefit.service

import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.stereotype.Service
import java.util.Date

@Service
class BenefitLaunchService(
    private val jobLauncher: JobLauncher,
    private val benefitIssuanceJob: Job
) {

    private val logger = LoggerFactory.getLogger(BenefitLaunchService::class.java)

    fun launchBenefitIssuance(): JobExecution {
        val params = JobParameters(
            mapOf("runTime" to JobParameter(Date(), Date::class.java))
        )
        val execution = jobLauncher.run(benefitIssuanceJob, params)
        logger.info("Benefit issuance job launched: id={}, status={}", execution.jobId, execution.status)
        return execution
    }
}
